/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import org.apache.kafka.common.utils.Time;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.keycloak.TokenVerifier;
import org.keycloak.crypto.AsymmetricSignatureVerifierContext;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.exceptions.TokenSignatureInvalidException;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JWKSUtils;
import org.keycloak.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.strimzi.kafka.oauth.validator.TokenValidationException.Status;

public class JWTSignatureValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JWTSignatureValidator.class);

    private static AtomicBoolean bouncyInstalled =  new AtomicBoolean(false);

    private static final TokenVerifier.TokenTypeCheck TOKEN_TYPE_CHECK = new TokenVerifier.TokenTypeCheck(TokenUtil.TOKEN_TYPE_BEARER);

    private final ScheduledExecutorService periodicScheduler;
    private final BackOffTaskScheduler fastScheduler;

    private final URI keysUri;
    private final String issuerUri;
    private final int maxStaleSeconds;
    private final boolean checkAccessTokenType;
    private final String audience;
    private final SSLSocketFactory socketFactory;
    private final HostnameVerifier hostnameVerifier;
    private final PrincipalExtractor principalExtractor;

    private long lastFetchTime;

    private Map<String, PublicKey> cache = Collections.emptyMap();
    private Map<String, PublicKey> oldCache = Collections.emptyMap();


    public JWTSignatureValidator(String keysEndpointUri,
                                 SSLSocketFactory socketFactory,
                                 HostnameVerifier verifier,
                                 PrincipalExtractor principalExtractor,
                                 String validIssuerUri,
                                 int refreshSeconds,
                                 int expirySeconds,
                                 boolean checkAccessTokenType,
                                 String audience,
                                 boolean enableBouncyCastleProvider,
                                 int bouncyCastleProviderPosition) {

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

        if (expirySeconds < refreshSeconds + 60) {
            throw new IllegalArgumentException("expirySeconds has to be at least 60 seconds longer than refreshSeconds");
        }
        this.maxStaleSeconds = expirySeconds;

        this.checkAccessTokenType = checkAccessTokenType;
        this.audience = audience;

        if (enableBouncyCastleProvider && !bouncyInstalled.getAndSet(true)) {
            int installedPosition = Security.insertProviderAt(new BouncyCastleProvider(), bouncyCastleProviderPosition);
            log.info("BouncyCastle security provider installed at position: " + installedPosition);

            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder("Installed security providers:\n");
                for (Provider p: Security.getProviders()) {
                    sb.append("  - " + p.toString() + "  [" + p.getClass().getName() + "]\n");
                    sb.append("   " + p.getInfo() + "\n");
                }
                log.debug(sb.toString());
            }
        }

        fetchKeys();

        periodicScheduler = setupRefreshKeysJob(refreshSeconds);

        fastScheduler = new BackOffTaskScheduler(periodicScheduler, () -> fetchKeys());
        fastScheduler.setCutoffIntervalSeconds(refreshSeconds);

        if (log.isDebugEnabled()) {
            log.debug("Configured JWTSignatureValidator:\n    keysEndpointUri: " + keysEndpointUri
                    + "\n    sslSocketFactory: " + socketFactory
                    + "\n    hostnameVerifier: " + hostnameVerifier
                    + "\n    principalExtractor: " + principalExtractor
                    + "\n    validIssuerUri: " + validIssuerUri
                    + "\n    certsRefreshSeconds: " + refreshSeconds
                    + "\n    certsExpirySeconds: " + expirySeconds
                    + "\n    checkAccessTokenType: " + checkAccessTokenType
                    + "\n    enableBouncyCastleProvider: " + enableBouncyCastleProvider
                    + "\n    bouncyCastleProviderPosition: " + bouncyCastleProviderPosition);
        }
    }

    private ScheduledExecutorService setupRefreshKeysJob(int refreshSeconds) {
        // set up periodic timer to update keys from server every refreshSeconds;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

        scheduler.scheduleAtFixedRate(() -> {
            try {
                fetchKeys();
            } catch (Exception e) {
                // Log, but don't rethrow the exception to prevent scheduler cancelling the scheduled job.
                log.error(e.getMessage(), e);
            }
        }, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);

        return scheduler;
    }

    private PublicKey getPublicKey(String id) {
        return getKeyUnlessStale(id);
    }

    private PublicKey getKeyUnlessStale(String id) {
        if (lastFetchTime + maxStaleSeconds * 1000L > System.currentTimeMillis()) {
            PublicKey result = cache.get(id);
            if (result == null) {
                log.warn("No public key for id: " + id);
            }
            return result;
        } else {
            log.warn("The cached public key with id '" + id + "' is expired!");
            return null;
        }
    }

    private void fetchKeys() {
        try {
            JSONWebKeySet jwks = HttpUtil.get(keysUri, socketFactory, hostnameVerifier, null, JSONWebKeySet.class);
            Map<String, PublicKey> newCache = JWKSUtils.getKeysForUse(jwks, JWK.Use.SIG);
            newCache = Collections.unmodifiableMap(newCache);
            if (!cache.equals(newCache)) {
                oldCache = cache;
                cache = newCache;
            }
            lastFetchTime = System.currentTimeMillis();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch public keys needed to validate JWT signatures: " + keysUri, ex);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST_OF_RETURN_VALUE",
            justification = "We tell TokenVerifier to parse AccessToken. It will return AccessToken or fail.")
    public TokenInfo validate(String token) {
        TokenVerifier<AccessToken> tokenVerifier = TokenVerifier.create(token, AccessToken.class);

        if (issuerUri != null) {
            tokenVerifier.realmUrl(issuerUri);
        }
        if (checkAccessTokenType) {
            tokenVerifier.withChecks(TOKEN_TYPE_CHECK);
        }
        if (audience != null) {
            tokenVerifier.audience(audience);
        }

        String kid = null;
        try {
            kid = tokenVerifier.getHeader().getKeyId();
        } catch (Exception e) {
            throw new TokenValidationException("Token signature validation failed: " + token, e)
                    .status(Status.INVALID_TOKEN);
        }

        AccessToken t;

        try {
            KeyWrapper keywrap = new KeyWrapper();
            PublicKey pub = getPublicKey(kid);
            if (pub == null) {
                if (oldCache.get(kid) != null) {
                    throw new TokenValidationException("Token validation failed: The signing key is no longer valid (kid:" + kid + ")");
                } else {
                    // Refresh keys asap but within reasonable non-flooding confines
                    fastScheduler.scheduleTask();
                    throw new TokenValidationException("Token validation failed: Unknown signing key (kid:" + kid + ")");
                }
            }
            keywrap.setPublicKey(pub);
            keywrap.setAlgorithm(tokenVerifier.getHeader().getAlgorithm().name());
            keywrap.setKid(kid);

            log.debug("Signature algorithm used: [" + pub.getAlgorithm() + "]");
            AsymmetricSignatureVerifierContext ctx = isAlgorithmEC(pub.getAlgorithm()) ?
                    new ECDSASignatureVerifierContext(keywrap) :
                    new AsymmetricSignatureVerifierContext(keywrap);
            tokenVerifier.verifierContext(ctx);

            log.debug("SignatureVerifierContext set to: " + ctx);

            tokenVerifier.verify();
            t = tokenVerifier.getToken();

        } catch (TokenSignatureInvalidException e) {
            throw new TokenSignatureException("Signature check failed:", e);
        } catch (TokenValidationException e) {
            // just rethrow
            throw e;
        } catch (Exception e) {
            throw new TokenValidationException("Token validation failed:", e);
        }

        long expiresMillis = t.getExpiration() * 1000L;
        if (Time.SYSTEM.milliseconds() > expiresMillis) {
            throw new TokenExpiredException("Token expired at: " + expiresMillis + " (" +
                    TimeUtil.formatIsoDateTimeUTC(expiresMillis) + " UTC)");
        }

        String principal = null;
        if (principalExtractor.isConfigured()) {
            principal = principalExtractor.getPrincipal(JSONUtil.asJson(t));
        }
        if (principal == null && !principalExtractor.isConfigured()) {
            principal = principalExtractor.getSub(t);
        }
        if (principal == null) {
            throw new RuntimeException("Failed to extract principal - check usernameClaim, fallbackUsernameClaim configuration");
        }
        return new TokenInfo(t, token, principal);
    }

    private static boolean isAlgorithmEC(String algorithm) {
        return "EC".equals(algorithm) || "ECDSA".equals(algorithm);
    }


    /**
     * Use daemon thread for refresh job
     */
    static class DaemonThreadFactory implements ThreadFactory {

        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }
    }
}
