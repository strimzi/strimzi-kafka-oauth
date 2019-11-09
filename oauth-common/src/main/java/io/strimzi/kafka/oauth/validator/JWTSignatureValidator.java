/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.strimzi.kafka.oauth.common.HttpUtil;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import org.apache.kafka.common.utils.Time;
import org.keycloak.TokenVerifier;
import org.keycloak.exceptions.TokenSignatureInvalidException;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JWKSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static io.strimzi.kafka.oauth.validator.TokenValidationException.Status;
import static org.keycloak.TokenVerifier.IS_ACTIVE;
import static org.keycloak.TokenVerifier.SUBJECT_EXISTS_CHECK;

public class JWTSignatureValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JWTSignatureValidator.class);

    private final ScheduledExecutorService scheduler;

    private final URI keysUri;
    private final String issuerUri;
    private final int maxStaleSeconds;
    private final boolean defaultChecks;
    private final boolean skipTypeCheck;
    private final String audience;
    private final SSLSocketFactory socketFactory;
    private final HostnameVerifier hostnameVerifier;

    private long lastFetchTime;

    private Map<String, PublicKey> cache = new ConcurrentHashMap<>();


    public JWTSignatureValidator(String keysEndpointUri,
                                 SSLSocketFactory socketFactory,
                                 HostnameVerifier verifier,
                                 String validIssuerUri,
                                 int refreshSeconds,
                                 int expirySeconds,
                                 boolean defaultChecks,
                                 boolean skipTypeCheck,
                                 String audience) {

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

        if (validIssuerUri == null) {
            throw new IllegalArgumentException("validIssuerUri == null");
        }
        this.issuerUri = validIssuerUri;

        if (expirySeconds < refreshSeconds + 60) {
            throw new IllegalArgumentException("expirySeconds has to be at least 60 seconds longer than refreshSeconds");
        }
        this.maxStaleSeconds = expirySeconds;

        this.defaultChecks = defaultChecks;
        this.skipTypeCheck = skipTypeCheck;
        this.audience = audience;

        fetchKeys();

        // set up periodic timer to update keys from server every refreshSeconds;
        scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

        scheduler.scheduleAtFixedRate(() -> fetchKeys(), refreshSeconds, refreshSeconds, TimeUnit.SECONDS);

        if (log.isDebugEnabled()) {
            log.debug("Configured JWTSignatureValidator:\n    keysEndpointUri: " + keysEndpointUri
                    + "\n    sslSocketFactory: " + socketFactory
                    + "\n    hostnameVerifier: " + hostnameVerifier
                    + "\n    validIssuerUri: " + validIssuerUri
                    + "\n    certsRefreshSeconds: " + refreshSeconds
                    + "\n    certsExpirySeconds: " + expirySeconds
                    + "\n    skipTypeCheck: " + skipTypeCheck);
        }
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
            cache = JWKSUtils.getKeysForUse(jwks, JWK.Use.SIG);
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

        if (defaultChecks) {
            if (skipTypeCheck) {
                tokenVerifier.withChecks(SUBJECT_EXISTS_CHECK, IS_ACTIVE);
            } else {
                tokenVerifier.withDefaultChecks();
            }
            tokenVerifier.realmUrl(issuerUri);
        }

        String kid = null;
        try {
            kid = tokenVerifier.getHeader().getKeyId();
        } catch (Exception e) {
            throw new TokenValidationException("Token signature validation failed: " + token, e)
                    .status(Status.INVALID_TOKEN);
        }
        tokenVerifier.publicKey(getPublicKey(kid));

        if (audience != null) {
            tokenVerifier.audience(audience);
        }

        AccessToken t;

        try {
            tokenVerifier.verify();
            t = tokenVerifier.getToken();

        } catch (TokenSignatureInvalidException e) {
            throw new TokenSignatureException("Signature check failed:", e);

        } catch (Exception e) {
            throw new TokenValidationException("Token validation failed:", e);
        }


        long expiresMillis = t.getExpiration() * 1000L;
        if (Time.SYSTEM.milliseconds() > expiresMillis) {
            throw new TokenExpiredException("Token expired at: " + expiresMillis + " (" +
                    TimeUtil.formatIsoDateTimeUTC(expiresMillis) + ")");
        }

        return new TokenInfo(t, token);
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
