/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.common.ConfigUtil;
import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.validator.JWTSignatureValidator;
import io.strimzi.kafka.oauth.validator.OAuthIntrospectionValidator;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.validator.TokenValidator;
import io.strimzi.kafka.oauth.validator.TokenValidationException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallback;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.representations.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static io.strimzi.kafka.oauth.common.DeprecationUtil.isAccessTokenJwt;
import static io.strimzi.kafka.oauth.common.LogUtil.mask;

public class JaasServerOauthValidatorCallbackHandler implements AuthenticateCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(JaasServerOauthValidatorCallbackHandler.class);

    private TokenValidator validator;

    private ServerConfig config;

    private boolean isJwt;

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {

        if (!OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(saslMechanism))    {
            throw new IllegalArgumentException(String.format("Unexpected SASL mechanism: %s", saslMechanism));
        }

        if (jaasConfigEntries.size() != 1) {
            throw new IllegalArgumentException("Exactly one jaasConfigEntry expected (size: " + jaasConfigEntries.size());
        }

        AppConfigurationEntry e = jaasConfigEntries.get(0);
        Properties p = new Properties();
        p.putAll(e.getOptions());
        config = new ServerConfig(p);

        isJwt = isAccessTokenJwt(config, log, "OAuth validator configuration error: ");

        validateConfig();

        SSLSocketFactory socketFactory = ConfigUtil.createSSLFactory(config);
        HostnameVerifier verifier = ConfigUtil.createHostnameVerifier(config);


        String jwksUri = config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI);

        boolean enableBouncy = config.getValueAsBoolean(ServerConfig.OAUTH_CRYPTO_PROVIDER_BOUNCYCASTLE, false);
        int bouncyPosition = config.getValueAsInt(ServerConfig.OAUTH_CRYPTO_PROVIDER_BOUNCYCASTLE_POSITION, 0);

        String validIssuerUri = config.getValue(ServerConfig.OAUTH_VALID_ISSUER_URI);

        if (validIssuerUri == null && config.getValueAsBoolean(ServerConfig.OAUTH_CHECK_ISSUER, true)) {
            throw new RuntimeException("OAuth validator configuration error: OAUTH_VALID_ISSUER_URI must be set or OAUTH_CHECK_ISSUER has to be set to 'false'");
        }

        boolean checkTokenType = isCheckAccessTokenType(config);

        PrincipalExtractor principalExtractor = new PrincipalExtractor(
                config.getValue(Config.OAUTH_USERNAME_CLAIM),
                config.getValue(Config.OAUTH_FALLBACK_USERNAME_CLAIM),
                config.getValue(Config.OAUTH_FALLBACK_USERNAME_PREFIX));

        if (jwksUri != null) {
            validator = new JWTSignatureValidator(
                    config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI),
                    socketFactory,
                    verifier,
                    principalExtractor,
                    validIssuerUri,
                    config.getValueAsInt(ServerConfig.OAUTH_JWKS_REFRESH_SECONDS, 300),
                    config.getValueAsInt(ServerConfig.OAUTH_JWKS_EXPIRY_SECONDS, 360),
                    checkTokenType,
                    null,
                    enableBouncy,
                    bouncyPosition
            );
        } else {
            validator = new OAuthIntrospectionValidator(
                    config.getValue(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI),
                    socketFactory,
                    verifier,
                    principalExtractor,
                    validIssuerUri,
                    config.getValue(ServerConfig.OAUTH_USERINFO_ENDPOINT_URI),
                    config.getValue(ServerConfig.OAUTH_VALID_TOKEN_TYPE),
                    config.getValue(Config.OAUTH_CLIENT_ID),
                    config.getValue(Config.OAUTH_CLIENT_SECRET),
                    null
            );
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isCheckAccessTokenType(Config config) {
        String legacy = config.getValue(ServerConfig.OAUTH_VALIDATION_SKIP_TYPE_CHECK);
        if (legacy != null) {
            log.warn("OAUTH_VALIDATION_SKIP_TYPE_CHECK is deprecated. Use OAUTH_CHECK_ACCESS_TOKEN_TYPE (with reverse meaning) instead.");
            if (config.getValue(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE) != null) {
                throw new RuntimeException("OAuth validator configuration error: can't use both OAUTH_CHECK_ACCESS_TOKEN_TYPE and OAUTH_VALIDATION_SKIP_TYPE_CHECK");
            }
        }
        return legacy != null ? !Config.isTrue(legacy) :
                config.getValueAsBoolean(ServerConfig.OAUTH_CHECK_ACCESS_TOKEN_TYPE, true);
    }

    private void validateConfig() {
        String jwksUri = config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI);
        String introspectUri = config.getValue(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI);

        if ((jwksUri == null) && (introspectUri == null)) {
            throw new RuntimeException("OAuth validator configuration error: either OAUTH_JWKS_ENDPOINT_URI (for fast local signature validation) or OAUTH_INTROSPECTION_ENDPOINT_URI (for using authorization server during validation) should be specified!");
        } else if ((jwksUri != null) && (introspectUri != null)) {
            throw new RuntimeException("OAuth validator configuration error: only one of OAUTH_JWKS_ENDPOINT_URI (for fast local signature validation) and OAUTH_INTROSPECTION_ENDPOINT_URI (for using authorization server during validation) can be specified!");
        }

        if (jwksUri != null && !isJwt) {
            throw new RuntimeException("OAuth validator configuration error: OAUTH_JWKS_ENDPOINT_URI (for fast local signature validation) is not compatible with OAUTH_ACCESS_TOKEN_IS_JWT=false");
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof OAuthBearerValidatorCallback) {
                handleCallback((OAuthBearerValidatorCallback) callback);
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    private void handleCallback(OAuthBearerValidatorCallback callback) {
        if (callback.tokenValue() == null) {
            throw new IllegalArgumentException("Callback has null token value!");
        }

        String token = callback.tokenValue();

        debugLogToken(token);

        try {
            TokenInfo ti = validateToken(token);

            callback.token(new BearerTokenWithPayload() {

                private Object payload;

                @Override
                public Object getPayload() {
                    return payload;
                }

                @Override
                public void setPayload(Object value) {
                    payload = value;
                }

                @Override
                public String value() {
                    return ti.token();
                }

                @Override
                public Set<String> scope() {
                    return ti.scope();
                }

                @Override
                public long lifetimeMs() {
                    return ti.expiresAtMs();
                }

                @Override
                public String principalName() {
                    return ti.principal();
                }

                @Override
                public Long startTimeMs() {
                    return ti.issuedAtMs();
                }

            });

        } catch (TokenValidationException e) {
            if (log.isDebugEnabled()) {
                log.debug("Token validation failed for token: " + mask(token), e);
            }
            callback.error(e.status(), null, null);

        } catch (RuntimeException e) {
            // Kafka ignores cause inside thrown exception, and doesn't log it
            if (log.isDebugEnabled()) {
                log.debug("Token validation failed due to runtime exception (network issue or misconfiguration): ", e);
            }
            // Extract cause and include it in a message string in order for it to be in the server log
            throw new AuthenticationException("Token validation failed due to runtime exception: " + getCauseMessage(e), e);

        } catch (Exception e) {
            // Log cause, because Kafka doesn't
            log.error("Unexpected failure during signature check:", e);

            throw new RuntimeException("Unexpected failure during signature check:", e);
        }
    }

    private static String getCauseMessage(Throwable e) {
        StringBuilder sb = new StringBuilder(e.toString());

        Throwable t = e;
        while ((t = t.getCause()) != null) {
            sb.append(", caused by: ").append(t.toString());
        }
        return sb.toString();
    }

    private TokenInfo validateToken(String token) {
        TokenInfo result = validator.validate(token);
        if (log.isDebugEnabled()) {
            log.debug("User validated (Principal:" + result.principal() + ")");
        }
        return result;
    }

    private void debugLogToken(String token) {
        if (!log.isDebugEnabled() || !isJwt) {
            return;
        }

        JWSInput parser;
        try {
            parser = new JWSInput(token);
            log.debug("Token: {}", parser.readContentAsString());
        } catch (JWSInputException e) {
            log.debug("[IGNORED] Token doesn't seem to be JWT token: " + mask(token), e);
            return;
        }

        try {
            AccessToken t = parser.readJsonContent(AccessToken.class);
            log.debug("Access token expires at (UTC): " + LocalDateTime.ofEpochSecond(t.getExp() == null ? 0 : t.getExp(), 0, ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME));
        } catch (JWSInputException e) {
            // Try parse as refresh token:
            log.debug("[IGNORED] Failed to parse JWT token's payload", e);
        }
    }
}
