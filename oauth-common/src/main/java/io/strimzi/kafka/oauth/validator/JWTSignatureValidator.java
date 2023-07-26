/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.jsonpath.JsonPathFilterQuery;
import io.strimzi.kafka.oauth.jsonpath.JsonPathQuery;
import io.strimzi.kafka.oauth.metrics.JwksHttpSensorKeyProducer;
import io.strimzi.kafka.oauth.metrics.SensorKeyProducer;
import io.strimzi.kafka.oauth.services.OAuthMetrics;
import io.strimzi.kafka.oauth.services.ServiceException;
import io.strimzi.kafka.oauth.services.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.strimzi.kafka.oauth.validator.TokenValidationException.Status;

/**
 * This class is responsible for validating the JWT token signatures during session authentication.
 * <p>
 * It performs fast local token validation without the need to immediately contact the authorization server.
 * for that it relies on the JWKS endpoint exposed at authorization server, which is a standard OAuth2 public endpoint
 * containing the information about public keys that can be used to validate JWT signatures.
 * </p>
 * <p>
 * A single threaded refresh job is run periodically or upon detecting an unknown signing key, that fetches the latest trusted public keys
 * for signature validation from authorization server. If the refresh job is unsuccessful it employs the so called 'exponential back-off'
 * to retry later in order to reduce any out-of-sync time with the authorization server while still not flooding the server
 * with endless consecutive requests.
 * </p>
 */
public class JWTSignatureValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JWTSignatureValidator.class);

    private static final DefaultJWSVerifierFactory VERIFIER_FACTORY = new DefaultJWSVerifierFactory();

    private final String validatorId;
    private final URI keysUri;
    private final String issuerUri;
    private final int maxStaleSeconds;
    private final boolean checkAccessTokenType;
    private final String audience;
    private final JsonPathFilterQuery customClaimMatcher;
    private final JsonPathQuery groupsQuery;
    private final String groupsDelimiter;
    private final SSLSocketFactory socketFactory;
    private final HostnameVerifier hostnameVerifier;
    private final PrincipalExtractor principalExtractor;
    private final boolean ignoreKeyUse;

    private final int connectTimeout;
    private final int readTimeout;

    private long lastFetchTime;

    private Map<String, PublicKey> cache = Collections.emptyMap();
    private Map<String, PublicKey> oldCache = Collections.emptyMap();

    private BackOffTaskScheduler fastScheduler;

    private final boolean enableMetrics;
    private final OAuthMetrics metrics;
    private final SensorKeyProducer jwksHttpSensorKeyProducer;
    private final boolean includeAcceptHeader;

    /**
     * Create a new instance.
     *
     * @param validatorId A unique id to associate with this validator for the purpose of validator lifecycle and metrics tracking
     * @param keysEndpointUri The JWKS endpoint url at the authorization server
     * @param socketFactory The optional SSL socket factory to use when establishing the connection to authorization server
     * @param verifier The optional hostname verifier used to validate the TLS certificate by the authorization server
     * @param principalExtractor The object used to extract the username from the JWT token
     * @param groupsClaimQuery The optional JSONPath query for group extraction
     * @param groupsClaimDelimiter The optional delimiter for group extraction
     * @param validIssuerUri The required value of the 'iss' claim in JWT token
     * @param refreshSeconds The optional time interval between two consecutive regular JWKS keys refresh runs
     * @param refreshMinPauseSeconds The optional minimum pause between two consecutive JWKS keys refreshes.
     * @param expirySeconds The maximum time to trust the unrefreshed JWKS keys. If keys are not successfully refreshed within this time, the validation will start failing.
     * @param ignoreKeyUse Should any key present in JWKS key set be considered a public key for signature checking
     * @param checkAccessTokenType Should the 'typ' claim in the token be validated (be equal to 'Bearer')
     * @param audience The optional audience
     * @param customClaimCheck The optional JSONPath filter query for additional custom claim checking
     * @param connectTimeoutSeconds The maximum time to wait for connection to authorization server to be established (in seconds)
     * @param readTimeoutSeconds The maximum time to wait for response from authorization server after connection has been established and request sent (in seconds)
     * @param enableMetrics The switch that enables metrics collection
     * @param failFast Should exception be thrown during initialisation if unable to retrieve JWKS keys
     * @param includeAcceptHeader Should we skip sending the Accept header when requesting JWKS keys
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public JWTSignatureValidator(String validatorId,
                                 String keysEndpointUri,
                                 SSLSocketFactory socketFactory,
                                 HostnameVerifier verifier,
                                 PrincipalExtractor principalExtractor,
                                 String groupsClaimQuery,
                                 String groupsClaimDelimiter,
                                 String validIssuerUri,
                                 int refreshSeconds,
                                 int refreshMinPauseSeconds,
                                 int expirySeconds,
                                 boolean ignoreKeyUse,
                                 boolean checkAccessTokenType,
                                 String audience,
                                 String customClaimCheck,
                                 int connectTimeoutSeconds,
                                 int readTimeoutSeconds,
                                 boolean enableMetrics,
                                 boolean failFast,
                                 boolean includeAcceptHeader) {

        if (validatorId == null) {
            throw new IllegalArgumentException("validatorId == null");
        }
        this.validatorId = validatorId;

        if (keysEndpointUri == null) {
            throw new IllegalArgumentException("keysEndpointUri == null");
        }
        try {
            this.keysUri = new URI(keysEndpointUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid keysEndpointUri: " + keysEndpointUri, e);
        }

        if (socketFactory != null && !"https".equals(keysUri.getScheme())) {
            throw new IllegalArgumentException("SSL socket factory set but keysEndpointUri not 'https'");
        }
        this.socketFactory = socketFactory;

        if (verifier != null && !"https".equals(keysUri.getScheme())) {
            throw new IllegalArgumentException("Certificate hostname verifier set but keysEndpointUri not 'https'");
        }
        this.hostnameVerifier = verifier;

        this.principalExtractor = principalExtractor;

        if (validIssuerUri != null) {
            try {
                new URI(validIssuerUri);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Value of validIssuerUri not a valid URI: " + validIssuerUri, e);
            }
        }
        this.issuerUri = validIssuerUri;

        validateRefreshConfig(refreshSeconds, expirySeconds);
        this.maxStaleSeconds = expirySeconds;

        this.checkAccessTokenType = checkAccessTokenType;
        this.audience = audience;
        this.customClaimMatcher = parseCustomClaimCheck(customClaimCheck);

        this.groupsQuery = parseGroupsQuery(groupsClaimQuery);
        this.groupsDelimiter = parseGroupsDelimiter(groupsClaimDelimiter);

        this.connectTimeout = connectTimeoutSeconds;
        this.readTimeout = readTimeoutSeconds;

        this.enableMetrics = enableMetrics;
        this.ignoreKeyUse = ignoreKeyUse;

        this.includeAcceptHeader = includeAcceptHeader;

        try {
            metrics = enableMetrics ? Services.getInstance().getMetrics() : null;
            jwksHttpSensorKeyProducer = new JwksHttpSensorKeyProducer(validatorId, keysUri);

            ScheduledExecutorService executor = setupExecutorAndFetchInitialKeys(refreshSeconds, refreshMinPauseSeconds, failFast);

            // set up periodic timer to trigger fastScheduler job every refreshSeconds
            setupRefreshKeysJob(executor, refreshSeconds);
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("Configured JWTSignatureValidator:"
                        + "\n    validatorId: " + validatorId
                        + "\n    keysEndpointUri: " + keysEndpointUri
                        + "\n    sslSocketFactory: " + socketFactory
                        + "\n    hostnameVerifier: " + hostnameVerifier
                        + "\n    principalExtractor: " + principalExtractor
                        + "\n    groupsClaimQuery: " + groupsClaimQuery
                        + "\n    groupsClaimDelimiter: " + groupsClaimDelimiter
                        + "\n    validIssuerUri: " + validIssuerUri
                        + "\n    certsRefreshSeconds: " + refreshSeconds
                        + "\n    certsRefreshMinPauseSeconds: " + refreshMinPauseSeconds
                        + "\n    certsExpirySeconds: " + expirySeconds
                        + "\n    certsIgnoreKeyUse: " + ignoreKeyUse
                        + "\n    checkAccessTokenType: " + checkAccessTokenType
                        + "\n    audience: " + audience
                        + "\n    customClaimCheck: " + customClaimCheck
                        + "\n    connectTimeoutSeconds: " + connectTimeoutSeconds
                        + "\n    readTimeoutSeconds: " + readTimeoutSeconds
                        + "\n    enableMetrics: " + enableMetrics
                        + "\n    failFast: " + failFast
                        + "\n    includeAcceptHeader: " + includeAcceptHeader);
            }
        }
    }

    private ScheduledExecutorService setupExecutorAndFetchInitialKeys(int refreshSeconds, int refreshMinPauseSeconds, boolean failFast) {

        // get the signing keys for signature validation before the first authorization requests start coming
        // fail fast if keys refresh doesn't work - it means network issues or authorization server not responding

        boolean initFetchFailed = false;
        try {
            fetchKeys();
        } catch (Exception e) {
            if (failFast) {
                throw e;
            } else {
                initFetchFailed = true;
                log.warn("[IGNORED] Fetching JWKS keys has failed, but fail-fast is disabled: ", e);
            }
        }

        // the single threaded executor for refreshing the keys
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

        // set up fast scheduler that refreshes keys on-demand, and keeps trying with exponential back-off until it succeeds
        fastScheduler = new BackOffTaskScheduler(executor, refreshMinPauseSeconds, refreshSeconds, this::fetchKeys);

        if (initFetchFailed) {
            fastScheduler.scheduleTask();
        }
        return executor;
    }

    private JsonPathFilterQuery parseCustomClaimCheck(String customClaimCheck) {
        if (customClaimCheck != null) {
            String query = customClaimCheck.trim();
            if (query.length() == 0) {
                throw new IllegalArgumentException("Value of customClaimCheck is empty");
            }
            return JsonPathFilterQuery.parse(query);
        }
        return null;
    }

    private JsonPathQuery parseGroupsQuery(String groupsQuery) {
        if (groupsQuery != null) {
            String query = groupsQuery.trim();
            if (query.length() == 0) {
                throw new IllegalArgumentException("Value of groupsClaimQuery is empty");
            }
            return JsonPathQuery.parse(query);
        }
        return null;
    }

    private String parseGroupsDelimiter(String groupsDelimiter) {
        if (groupsDelimiter != null) {
            if (groupsDelimiter.length() == 0) {
                throw new IllegalArgumentException("Value of groupsClaimDelimiter is empty");
            }
        }
        return ",";
    }

    private void validateRefreshConfig(int refreshSeconds, int expirySeconds) {
        if (refreshSeconds <= 0) {
            throw new IllegalArgumentException("refreshSeconds has to be a positive number - (refreshSeconds=" + refreshSeconds + ")");
        }

        if (expirySeconds < refreshSeconds + 60) {
            throw new IllegalArgumentException("expirySeconds has to be at least 60 seconds longer than refreshSeconds - (expirySeconds="
                    + expirySeconds + ", refreshSeconds=" + refreshSeconds + ")");
        }
    }

    /**
     * Set up a regular keys refresh job running on a fixed schedule every <em>refreshSeconds</em>.
     * Use the fastScheduler for actual keys refresh which means that a minimum pause between two consecutive refreshes
     * is enforced, and if the keys refresh fails it keeps re-trying using the exponential backoff.
     *
     * @param refreshSeconds The refresh period
     */
    private void setupRefreshKeysJob(ScheduledExecutorService executor, int refreshSeconds) {

        executor.scheduleAtFixedRate(() -> {
            try {
                fastScheduler.scheduleTask();
            } catch (Throwable e) {
                // Log, but don't rethrow the exception to prevent scheduler cancelling the scheduled job.
                log.error("{}", e.getMessage(), e);
            }
        }, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
    }

    private PublicKey getPublicKey(String id) {
        return getKeyUnlessStale(id);
    }

    private PublicKey getKeyUnlessStale(String id) {
        if (lastFetchTime + maxStaleSeconds * 1000L > System.currentTimeMillis()) {
            PublicKey result = cache.get(id);
            if (result == null) {
                log.warn("No public key for id: {}", id);
            }
            return result;
        } else {
            log.warn("The cached public key with id '{}' is expired!", id);
            return null;
        }
    }

    private void fetchKeys() {
        long requestStartTime = System.currentTimeMillis();
        try {
            String response = HttpUtil.get(keysUri, socketFactory, hostnameVerifier, null, String.class, connectTimeout, readTimeout, includeAcceptHeader);
            addJwksHttpMetricSuccessTime(requestStartTime);

            Map<String, PublicKey> newCache = new HashMap<>();
            JWKSet jwks = JWKSet.parse(response);

            for (JWK jwk : jwks.getKeys()) {
                if (ignoreKeyUse || KeyUse.SIGNATURE.equals(jwk.getKeyUse())) {

                    PublicKey publicKey;

                    if (jwk instanceof ECKey) {
                        publicKey = ((ECKey) jwk).toPublicKey();
                    } else if (jwk instanceof RSAKey) {
                        publicKey = ((RSAKey) jwk).toPublicKey();
                    } else {
                        log.warn("Unsupported JWK key type: {}", jwk.getKeyType());
                        continue;
                    }
                    newCache.put(jwk.getKeyID(), publicKey);
                }
            }

            newCache = Collections.unmodifiableMap(newCache);
            if (!cache.equals(newCache)) {
                log.info("JWKS keys change detected. Keys updated.");
                oldCache = cache;
                cache = newCache;
            }
            lastFetchTime = System.currentTimeMillis();

        } catch (Throwable ex) {
            addJwksHttpMetricErrorTime(ex, requestStartTime);
            throw new ServiceException("Failed to fetch public keys needed to validate JWT signatures: " + keysUri, ex);
        }
    }

    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST_OF_RETURN_VALUE",
            justification = "We tell TokenVerifier to parse AccessToken. It will return AccessToken or fail.")
    public TokenInfo validate(String token) {

        SignedJWT jwt;
        String kid;
        try {
            jwt = SignedJWT.parse(token);
            kid = jwt.getHeader().getKeyID();
        } catch (Exception e) {
            throw new TokenValidationException("Token validation failed: Failed to parse JWT.", e)
                    .status(Status.INVALID_TOKEN);
        }

        JsonNode t;
        try {
            PublicKey pub = getPublicKey(kid);
            if (pub == null) {
                if (oldCache.get(kid) != null) {
                    throw new TokenValidationException("Token validation failed: The signing key is no longer valid (kid:" + kid + ")");
                } else {
                    // Request quick keys refresh
                    try {
                        fastScheduler.scheduleTask();
                    } catch (RuntimeException e) {
                        log.error("Failed to reschedule JWKS keys refresh: ", e);
                    }
                    throw new TokenValidationException("Token validation failed: Unknown signing key (kid:" + kid + ")");
                }
            }

            if (!jwt.verify(VERIFIER_FACTORY.createJWSVerifier(jwt.getHeader(), pub))) {
                throw new TokenSignatureException("Signature check failed: Invalid token signature");
            }
            t = JSONUtil.asJson(jwt.getPayload().toJSONObject());

        } catch (TokenValidationException e) {
            // just rethrow
            throw e;
        } catch (Exception e) {
            throw new TokenValidationException("Token validation failed", e);
        }

        JsonNode exp = t.get(TokenInfo.EXP);
        if (exp == null) {
            throw new TokenValidationException("Token validation failed: Expiry not set");
        }
        long expiresMillis = exp.asInt(0) * 1000L;
        if (System.currentTimeMillis() > expiresMillis) {
            throw new TokenExpiredException("Token expired at: " + expiresMillis + " (" +
                    TimeUtil.formatIsoDateTimeUTC(expiresMillis) + " UTC)");
        }

        validateTokenPayload(t);

        if (customClaimMatcher != null) {
            if (!customClaimMatcher.matches(t)) {
                throw new TokenValidationException("Token validation failed: Custom claim check failed");
            }
        }

        String principal = extractPrincipal(t);
        Set<String> groups = extractGroups(t);

        return new TokenInfo(t, token, principal, groups);
    }

    private String extractPrincipal(JsonNode tokenJson) {
        String principal = null;

        if (principalExtractor.isConfigured()) {
            principal = principalExtractor.getPrincipal(tokenJson);
        }
        if (principal == null && !principalExtractor.isConfigured()) {
            principal = principalExtractor.getSub(tokenJson);
        }
        if (principal == null) {
            throw new ValidationException("Failed to extract principal - check usernameClaim, fallbackUsernameClaim configuration");
        }
        return principal;
    }

    private Set<String> extractGroups(JsonNode tokenJson) {
        if (groupsQuery == null) {
            return null;
        }
        JsonNode result = groupsQuery.apply(tokenJson);
        if (result == null) {
            return null;
        }
        List<String> groups = JSONUtil.asListOfString(result, groupsDelimiter != null ? groupsDelimiter : ",");
        // sanitize the result
        Set<String> groupSet = groups.stream().map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toSet());

        return groupSet.isEmpty() ? null : groupSet;
    }

    private void validateTokenPayload(JsonNode token) {
        if (issuerUri != null) {
            JsonNode iss = token.get(TokenInfo.ISS);
            if (iss == null) {
                throw new TokenValidationException("Token validation failed: Issuer not set");
            }
            String issuer = iss.asText();
            if (!issuerUri.equals(issuer)) {
                throw new TokenValidationException("Token validation failed: Issuer not allowed: " + issuer);
            }
        }
        if (checkAccessTokenType) {
            JsonNode type = token.get(TokenInfo.TYP);
            if (type == null) {
                type = token.get(TokenInfo.TOKEN_TYPE);
                if (type == null) {
                    throw new TokenValidationException("Token validation failed: Token type not set ('token_type' or 'typ' claim not present)");
                }
            }
            String value = type.asText();
            if (!"Bearer".equals(value)) {
                throw new TokenValidationException("Token validation failed: Token type not allowed: " + value);
            }
        }
        if (audience != null) {
            JsonNode audNode = token.get(TokenInfo.AUD);
            List<String> aud = audNode == null ? Collections.emptyList() : JSONUtil.asListOfString(audNode);
            if (!aud.contains(audience)) {
                throw new TokenValidationException("Token validation failed: Expected audience not available in the token");
            }
        }
    }

    @Override
    public String getValidatorId() {
        return validatorId;
    }


    private void addJwksHttpMetricSuccessTime(long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(jwksHttpSensorKeyProducer.successKey(), System.currentTimeMillis() - startTimeMs);
        }
    }

    private void addJwksHttpMetricErrorTime(Throwable e, long startTimeMs) {
        if (enableMetrics) {
            metrics.addTime(jwksHttpSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTimeMs);
        }
    }
}
