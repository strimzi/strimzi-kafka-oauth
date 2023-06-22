/*
 * Copyright 2017-2023, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.HttpException;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.services.ServiceException;
import io.strimzi.kafka.oauth.services.Services;
import io.strimzi.kafka.oauth.validator.DaemonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static io.strimzi.kafka.oauth.common.JSONUtil.asSetOfNodes;
import static io.strimzi.kafka.oauth.common.LogUtil.mask;

/**
 * The class that handles grants cache and services to maintain it.
 * <p>
 * Grants are cached per user id. All sessions by the same user id share the cached grants.
 * The underlying authentication for fetching the grants is still an access token. Different sessions
 * with the same user id can authenticate using different access tokens. The assumption is that grants resolution
 * for the user id produces the same set of grants regardless of the access token used to obtain grants.
 * Access token for a specific user id with the longest expiry is held in the cache for a background grants refresh job.
 *
 * The instance of this class runs three background services:
 * <ul>
 *     <li><em>Grants Refresh Scheduler</em> A ScheduledExecutorService that wakes up on a fixed period and scans the grants cache,
 *     queueing a refresh for every grant that has a valid access token, and hasn't been idle for more than the max idle time.
 *     Grants with expired access tokens and idled out grants are removed from cache.</li>
 *     <li><em>Grants Refresh Worker</em> A fixed thread pool ExecutorService that executes refresh jobs queued by Grants Refresh Scheduler</li>
 *     <li><em>GC Worker</em> A ScheduledExecutorService that wakes up on a fixed period and removes grants for user ids for which there are no active sessions.
 *     It removes such grants from cache, so they are no longer refreshed.</li>
 * </ul>
 *
 * When a new session triggers a first authorize() call, the grants are looked up in the cache. If grants are available they are returned and used.
 * If not, they are fetched. If during that time any authorize() call comes in for the same user id it is made to wait for the results of the existing grants fetching job to be complete.
 * This way we prevent multiple fetches of grants for the same user id when they are not yet in the cache. When grant is successfully retrieved it is cached,
 * and returned to all the waiting authorize() threads. If fetching of grants is unsuccessful they all receive an exception, and all the authorize() actions are denied.
 * In that case, since there is no grant cached for user id, the next authorize() request will attempt to fetch grants for that user id again using the current session's access token.
 */
@SuppressFBWarnings("THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION")
class GrantsHandler implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(GrantsHandler.class);

    private final HashMap<String, Info> grantsCache = new HashMap<>();

    /** Helper methods to queue up grants refresh jobs per user id */
    private final Semaphores<JsonNode> semaphores = new Semaphores<>();

    /** Grants Refresh Worker */
    private final ExecutorService refreshWorker;

    /** GC Worker */
    private final ScheduledExecutorService gcWorker;

    /** Grants Refresh Scheduler */
    private final ScheduledExecutorService refreshScheduler;

    private final long gcPeriodMillis;

    /** Externally provided function that performs an HTTP request to the Keycloak token / grants endpoint */
    private final Function<String, JsonNode> authorizationGrantsProvider;

    /** Maximum number of retries to attempt if the grants refresh fails */
    private final int httpRetries;

    /** Maximum idle time in millis for a cached grant (time in which a grant has not been accessed in cache) */
    private final long grantsMaxIdleMillis;

    /** Used to check when the last gc run was performed in order to prevent gc runs from queueing up */
    private long lastGcRunTimeMillis;

    /**
     * Cleanup background threads.
     */
    @Override
    public void close() {
        shutDownExecutorService("grants refresh scheduler", refreshScheduler);
        shutDownExecutorService("grants refresh worker", refreshWorker);
        shutDownExecutorService("gc worker", gcWorker);
    }

    private void shutDownExecutorService(String name, ExecutorService service) {
        try {
            log.trace("Shutting down {} [{}]", name, service);
            service.shutdownNow();
            if (!service.awaitTermination(10, TimeUnit.SECONDS)) {
                log.debug("[IGNORED] Failed to cleanly shutdown {} within 10 seconds", name);
            }
        } catch (Throwable t) {
            log.warn("[IGNORED] Failed to cleanly shutdown {}: ", name, t);
        }
    }

    /**
     * A cached record with grants JSON, access token used to retrieve grants, token expiry info and last usage info.
     */
    static class Info {
        private volatile String accessToken;
        private volatile JsonNode grants;
        private volatile long expiresAt;
        private volatile long lastUsed;

        Info(String accessToken, long expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
            this.lastUsed = System.currentTimeMillis();
        }

        synchronized void updateTokenIfExpiresLater(BearerTokenWithPayload token) {
            lastUsed = System.currentTimeMillis();
            if (token.lifetimeMs() > expiresAt) {
                accessToken = token.value();
                expiresAt = token.lifetimeMs();
            }
        }

        String getAccessToken() {
            return accessToken;
        }

        JsonNode getGrants() {
            return grants;
        }

        void setGrants(JsonNode newGrants) {
            grants = newGrants;
        }

        long getLastUsed() {
            return lastUsed;
        }

        boolean isExpiredAt(long timestamp) {
            return expiresAt < timestamp;
        }
    }

    static class Future implements java.util.concurrent.Future<JsonNode> {

        private final java.util.concurrent.Future<JsonNode> delegate;
        private final String userId;
        private final Info grantsInfo;

        /**
         * Create a new instance
         *
         * @param future Original future instance to wrap
         */
        @SuppressFBWarnings("EI_EXPOSE_REP2")
        public Future(String userId, GrantsHandler.Info grantsInfo, java.util.concurrent.Future<JsonNode> future) {
            this.userId = userId;
            this.grantsInfo = grantsInfo;
            this.delegate = future;
        }

        /**
         * Get a <code>BearerTokenWithPayload</code> object representing a session
         *
         * @return A token instance
         */
        @SuppressFBWarnings("EI_EXPOSE_REP")
        public GrantsHandler.Info getGrantsInfo() {
            return grantsInfo;
        }

        public String getUserId() {
            return userId;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public JsonNode get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        @Override
        public JsonNode get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }

    /**
     * Create a new GrantsHandler instance
     *
     * @param grantsRefreshPeriodSeconds Number of seconds between two consecutive grants refresh job runs
     * @param grantsRefreshPoolSize The number of threads over which to spread a grants refresh job run
     * @param grantsMaxIdleTimeSeconds An idle time in seconds during which the cached grant wasn't accesses by any session so is deemed unneeded, and can be garbage collected
     * @param httpGrantsProvider A function with grant fetching logic
     * @param httpRetries A maximum number of repeated attempts if a grants request to the token endpoint fails in unexpected way
     * @param gcPeriodSeconds Number of seconds between two consecutive grants garbage collection job runs
     */
    @SuppressFBWarnings("MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR")
    GrantsHandler(int grantsRefreshPeriodSeconds, int grantsRefreshPoolSize, int grantsMaxIdleTimeSeconds, Function<String, JsonNode> httpGrantsProvider, int httpRetries, int gcPeriodSeconds) {
        this.authorizationGrantsProvider = httpGrantsProvider;
        this.httpRetries = httpRetries;

        if (grantsMaxIdleTimeSeconds <= 0) {
            throw new IllegalArgumentException("grantsMaxIdleTimeSeconds <= 0");
        }
        this.grantsMaxIdleMillis = grantsMaxIdleTimeSeconds * 1000L;

        DaemonThreadFactory daemonThreadFactory = new DaemonThreadFactory();
        if (grantsRefreshPeriodSeconds > 0) {
            this.refreshWorker = Executors.newFixedThreadPool(grantsRefreshPoolSize, daemonThreadFactory);

            // Set up periodic timer to fetch grants for active sessions every refresh seconds
            this.refreshScheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory);
            refreshScheduler.scheduleAtFixedRate(this::performRefreshGrantsRun, grantsRefreshPeriodSeconds, grantsRefreshPeriodSeconds, TimeUnit.SECONDS);
        } else {
            this.refreshWorker = null;
            this.refreshScheduler = null;
        }

        if (gcPeriodSeconds <= 0) {
            throw new IllegalArgumentException("gcPeriodSeconds <= 0");
        }
        this.gcPeriodMillis = gcPeriodSeconds * 1000L;
        this.gcWorker = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory);
        gcWorker.scheduleAtFixedRate(this::gcGrantsCacheRunnable, gcPeriodSeconds, gcPeriodSeconds, TimeUnit.SECONDS);
    }

    /**
     * The function to call as a periodic job
     */
    private void gcGrantsCacheRunnable() {
        long timePassedSinceGc = System.currentTimeMillis() - lastGcRunTimeMillis;
        if (timePassedSinceGc < gcPeriodMillis - 1000) { // give or take one second
            log.debug("Skipped queued gc run (last run {} ms ago)", timePassedSinceGc);
            return;
        }
        lastGcRunTimeMillis = System.currentTimeMillis();
        gcGrantsCache();
    }

    /**
     * Perform one garbage collection run
     */
    private void gcGrantsCache() {
        long start = System.currentTimeMillis();
        HashSet<String> userIds = new HashSet<>(Services.getInstance().getSessions().map(BearerTokenWithPayload::principalName));
        log.trace("Grants gc: active users: {}", userIds);
        int beforeSize;
        int afterSize;
        synchronized (grantsCache) {
            beforeSize = grantsCache.size();
            // keep the active sessions, remove grants for unknown user ids
            grantsCache.keySet().retainAll(userIds);
            afterSize = grantsCache.size();
        }
        log.debug("Grants gc: active users count: {}, grantsCache size before: {}, grantsCache size after: {}, gc duration: {} ms", userIds.size(), beforeSize, afterSize, System.currentTimeMillis() - start);
    }

    /**
     * Fetch grants for the user using the access token contained in the grantsInfo,
     * and save the result to the grantsInfo object.
     *
     * @param userId User id
     * @param grantsInfo Grants info object representing the cached grants entry for the user
     * @return Obtained grants
     */
    private JsonNode fetchAndSaveGrants(String userId, Info grantsInfo) {
        // If no grants found, fetch grants from server
        JsonNode grants = null;
        try {
            log.debug("Fetching grants from Keycloak for user {}", userId);
            grants = fetchGrantsWithRetry(grantsInfo.getAccessToken());
            if (grants == null) {
                log.debug("Received null grants for user: {}, token: {}", userId, mask(grantsInfo.getAccessToken()));
                grants = JSONUtil.newObjectNode();
            }
        } catch (HttpException e) {
            if (e.getStatus() == 403) {
                grants = JSONUtil.newObjectNode();
            } else {
                log.warn("Unexpected status while fetching authorization data - will retry next time: {}", e.getMessage());
            }
        }
        if (grants != null) {
            // Store authz grants in the token, so they are available for subsequent requests
            log.debug("Saving non-null grants for user: {}, token: {}", userId, mask(grantsInfo.getAccessToken()));
            grantsInfo.setGrants(grants);
        }
        return grants;
    }

    /**
     * Method that performs the POST request to fetch grants for the token.
     * In case of a connection failure or a non-200 status response this method immediately retries the request if so configured.
     * <p>
     * Status 401 does not trigger a retry since it is used to signal an invalid token.
     * Status 403 does not trigger a retry either since it signals no permissions.
     *
     * @param token The raw access token
     * @return Grants JSON response
     */
    private JsonNode fetchGrantsWithRetry(String token) {

        int i = 0;
        do {
            i += 1;

            try {
                if (i > 1) {
                    log.debug("Grants request attempt no. {}", i);
                }
                return authorizationGrantsProvider.apply(token);

            } catch (Exception e) {
                if (e instanceof HttpException) {
                    int status = ((HttpException) e).getStatus();
                    if (403 == status || 401 == status) {
                        throw e;
                    }
                }

                if (log.isInfoEnabled()) {
                    log.info("Failed to fetch grants on try no. {}", i, e);
                }
                if (i > httpRetries) {
                    log.debug("Failed to fetch grants after {} tries", i);
                    throw e;
                }
            }
        } while (true);
    }

    /**
     * Perform a single grants refresh run
     */
    private void performRefreshGrantsRun() {
        try {
            log.debug("Refreshing authorization grants ... [{}]", this);

            HashMap<String, Info> workmap;
            synchronized (grantsCache) {
                workmap = new HashMap<>(grantsCache);
            }

            Set<Map.Entry<String, Info>> entries = workmap.entrySet();
            List<Future> scheduled = new ArrayList<>(entries.size());
            long now = System.currentTimeMillis();

            for (Map.Entry<String, Info> ent : entries) {
                String userId = ent.getKey();
                Info grantsInfo = ent.getValue();
                if (grantsInfo.getLastUsed() < now - grantsMaxIdleMillis) {
                    log.debug("Skipping refreshing grants for user '{}' due to max idle time.", userId);
                    removeUserFromCacheIfExpiredOrIdle(userId);
                }
                scheduled.add(new Future(userId, grantsInfo, refreshWorker.submit(() -> {

                    if (log.isTraceEnabled()) {
                        log.trace("Fetch grants for user: {}, token: {}", userId, mask(grantsInfo.getAccessToken()));
                    }

                    JsonNode newGrants;
                    try {
                        newGrants = fetchGrantsWithRetry(grantsInfo.getAccessToken());
                    } catch (HttpException e) {
                        // Handle Keycloak token / grants endpoint returning status 403 Forbidden
                        // 403 happens when no policy matches the token - thus there are no grants, no permission granted
                        if (403 == e.getStatus()) {
                            newGrants = JSONUtil.newObjectNode();
                        } else {
                            throw e;
                        }
                    }
                    JsonNode oldGrants = grantsInfo.getGrants();
                    if (!semanticGrantsEquals(newGrants, oldGrants)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Grants have changed for user: {}; before: {}; after: {}", userId, oldGrants, newGrants);
                        }
                        grantsInfo.setGrants(newGrants);
                    }

                    // Only added here to allow compiler to resolve the lambda as a Callable<JsonNode>
                    return newGrants;
                })));
            }

            for (GrantsHandler.Future f : scheduled) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    final Throwable cause = e.getCause();
                    if (cause instanceof HttpException) {
                        log.debug("[IGNORED] Failed to fetch grants for user: {}", cause.getMessage());
                        // Handle Keycloak token / grants endpoint returning status 401 Unauthorized
                        // 401 happens when the token has expired or has been revoked
                        if (401 == ((HttpException) cause).getStatus()) {
                            grantsCache.remove(f.getUserId());
                            log.debug("Removed user from grants cache: {}", f.getUserId());
                            Services.getInstance().getSessions().removeAllWithMatchingAccessToken(f.getGrantsInfo().accessToken);
                            continue;
                        }
                    }

                    log.warn("[IGNORED] Failed to fetch grants for user: {}", e.getMessage(), e);

                } catch (Throwable e) {
                    if (log.isWarnEnabled()) {
                        log.warn("[IGNORED] Failed to fetch grants for user: {}, token: {} - {}", f.getUserId(), mask(f.getGrantsInfo().accessToken), e.getMessage(), e);
                    }
                }
            }

        } catch (Throwable t) {
            // Log, but don't rethrow the exception to prevent scheduler cancelling the scheduled job.
            log.error("{}", t.getMessage(), t);
        } finally {
            log.debug("Done refreshing grants");
        }
    }

    /**
     * Remove grants for the given user from the cache if the access token for it is expired or there was no access for the
     * maximum idle time.
     *
     * @param userId User id
     */
    private void removeUserFromCacheIfExpiredOrIdle(String userId) {
        synchronized (grantsCache) {
            Info info = grantsCache.get(userId);
            if (info != null) {
                long now = System.currentTimeMillis();
                boolean isIdle = info.getLastUsed() < now - grantsMaxIdleMillis;
                if (isIdle || info.isExpiredAt(now)) {
                    log.debug("Removed user from grants cache due to {}: {}", isIdle ? "'idle'" : "'expired'", userId);
                    grantsCache.remove(userId);
                }
            }
        }
    }

    /**
     * Lookup the grants cache given the token
     *
     * @param token A token object
     * @return Grants info object representing a grants cache entry
     */
    Info getGrantsInfoFromCache(BearerTokenWithPayload token) {
        Info grantsInfo;

        synchronized (grantsCache) {
            grantsInfo = grantsCache.computeIfAbsent(token.principalName(),
                k -> new Info(token.value(), token.lifetimeMs()));
        }

        // Always keep the longest lasting access token in the cache
        grantsInfo.updateTokenIfExpiresLater(token);
        return grantsInfo;
    }

    /**
     * This method ensures that for any particular user id there is a single grants fetching operation in progress at any one time.
     * <p>
     * If for the current user there is a grants fetch operation in progress the thread simply waits for the results of that operation.
     * This is only relevant if there are no grants for the user available in grants cache.
     *
     * @param userId User id
     * @param grantsInfo Grants info object representing the cached grants entry for the user
     * @return Grants JSON
     */
    JsonNode fetchGrantsForUserOrWaitForDelivery(String userId, Info grantsInfo) {
        // Fetch authorization grants
        Semaphores.SemaphoreResult<JsonNode> semaphore = semaphores.acquireSemaphore(userId);

        // Try to acquire semaphore for fetching grants
        if (semaphore.acquired()) {
            // If acquired
            try {
                log.debug("Acquired semaphore for '{}'", userId);
                JsonNode grants = fetchAndSaveGrants(userId, grantsInfo);
                semaphore.future().complete(grants);
                return grants;

            } catch (Throwable t) {
                semaphore.future().completeExceptionally(t);
                throw t;
            } finally {
                semaphores.releaseSemaphore(userId);
                log.debug("Released semaphore for '{}'", userId);
            }

        } else {
            try {
                log.debug("Waiting on another thread to get grants for '{}'", userId);
                return semaphore.future().get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ServiceException) {
                    throw (ServiceException) cause;
                } else {
                    throw new ServiceException("ExecutionException waiting for grants result: ", e);
                }
            } catch (InterruptedException e) {
                throw new ServiceException("InterruptedException waiting for grants result: ", e);
            }
        }
    }

    /**
     * This method compares two JSON objects with grants for semantic equality.
     * <p>
     * Keycloak sometimes returns grants for the user in different order, treating the JSON array as a Set.
     * When checking for equality we should also treat the JSON array as a Set.
     *
     * @param grants1 First JSON array containing grants
     * @param grants2 Second JSON array containing grants
     * @return true if grants objects are semantically equal
     */
    private static boolean semanticGrantsEquals(JsonNode grants1, JsonNode grants2) {
        if (grants1 == grants2) return true;
        if (grants1 == null) {
            throw new IllegalArgumentException("Invalid grants: null");
        }
        if (grants2 == null) {
            return false;
        }
        if (!grants1.isArray()) {
            throw new IllegalArgumentException("Invalid grants: not a JSON array");
        }
        if (!grants2.isArray()) {
            throw new IllegalArgumentException("Invalid grants: not a JSON array");
        }
        return asSetOfNodes((ArrayNode) grants1).equals(asSetOfNodes((ArrayNode) grants2));
    }
}
