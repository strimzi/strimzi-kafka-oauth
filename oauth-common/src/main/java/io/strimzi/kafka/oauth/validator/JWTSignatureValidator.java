/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

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

public class JWTSignatureValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JWTSignatureValidator.class);

    private final ScheduledExecutorService scheduler;

    private final URI keysUri;
    private final String issuerUri;
    private final int maxStaleSeconds;
    private final boolean defaultChecks;
    private final String audience;
    private final SSLSocketFactory socketFactory;

    private long lastFetchTime;

    private Map<String, PublicKey> cache = new ConcurrentHashMap<>();


    public JWTSignatureValidator(String keysEndpointUri,
                                 SSLSocketFactory socketFactory,
                                 String validIssuerUri,
                                 int refreshSeconds,
                                 int expirySeconds,
                                 boolean defaultChecks,
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
            throw new IllegalArgumentException("SSL socket factory set but keysEndpointUri not 'https://'");
        }
        this.socketFactory = socketFactory;

        if (validIssuerUri == null) {
            throw new IllegalArgumentException("validIssuerUri == null");
        }
        this.issuerUri = validIssuerUri;

        if (expirySeconds < refreshSeconds + 60) {
            throw new IllegalArgumentException("expirySeconds has to be at least 60 seconds longer than refreshSeconds");
        }
        this.maxStaleSeconds = expirySeconds;

        this.defaultChecks = defaultChecks;
        this.audience = audience;

        fetchKeys();

        // set up periodic timer to update keys from server every refreshSeconds;
        scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                return t;
            }
        });

        scheduler.scheduleAtFixedRate(() -> fetchKeys(), refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
    }

    private PublicKey getPublicKey(String id) {
        return getKeyUnlessStale(id);
    }

    private PublicKey getKeyUnlessStale(String id) {
        if (lastFetchTime + maxStaleSeconds * 1000 > System.currentTimeMillis()) {
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
            JSONWebKeySet jwks = HttpUtil.get(keysUri, socketFactory,null, JSONWebKeySet.class);
            cache = JWKSUtils.getKeysForUse(jwks, JWK.Use.SIG);
            lastFetchTime = System.currentTimeMillis();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch public keys needed to validate JWT signatures: " + keysUri, ex);
        }
    }

    public TokenInfo validate(String token) {
        TokenVerifier<AccessToken> tokenVerifier = TokenVerifier.create(token, AccessToken.class);

        if (defaultChecks) {
            tokenVerifier
                    .withDefaultChecks()
                    .realmUrl(issuerUri);
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
}
