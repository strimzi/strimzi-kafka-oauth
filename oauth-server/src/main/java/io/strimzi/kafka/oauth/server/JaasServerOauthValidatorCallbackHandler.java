package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.Config;
import io.strimzi.kafka.oauth.validator.JWTSignatureValidator;
import io.strimzi.kafka.oauth.validator.OAuthIntrospectionValidator;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.validator.TokenValidator;
import io.strimzi.kafka.oauth.validator.TokenValidationException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallback;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.representations.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static io.strimzi.kafka.oauth.common.JSONUtil.getClaimFromJWT;

public class JaasServerOauthValidatorCallbackHandler implements AuthenticateCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(JaasServerOauthValidatorCallbackHandler.class);

    private TokenValidator validator;

    private ServerConfig config;

    private String usernameClaim;

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {

        if (!OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(saslMechanism))    {
            throw new IllegalArgumentException(String.format("Unexpected SASL mechanism: %s", saslMechanism));
        }

        if (jaasConfigEntries.size() != 1) {
            throw new IllegalArgumentException("Exactly one jaasConfigEntry expected (size: " + jaasConfigEntries.size());
        }

        for (AppConfigurationEntry e: jaasConfigEntries) {
            Properties p = new Properties();
            p.putAll(e.getOptions());
            config = new ServerConfig(p);
            break;
        }

        validateConfig();

        String jwksUri = config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI);
        if (jwksUri != null) {

            validator = new JWTSignatureValidator(
                    config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI),
                    config.getValue(ServerConfig.OAUTH_VALID_ISSUER_URI),
                    config.getValueAsInt(ServerConfig.OAUTH_JWKS_EXPIRY_SECONDS, 360),
                    config.getValueAsInt(ServerConfig.OAUTH_JWKS_REFRESH_SECONDS, 300),
                    config.getValueAsBoolean(ServerConfig.OAUTH_VALIDATE_COMMON_CHECKS, true),
                    config.getValue(ServerConfig.OAUTH_VALIDATE_AUDIENCE)
            );
        } else {

            validator = new OAuthIntrospectionValidator(
                    config.getValue(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI),
                    config.getValue(ServerConfig.OAUTH_VALID_ISSUER_URI),
                    config.getValue(Config.OAUTH_CLIENT_ID),
                    config.getValue(Config.OAUTH_CLIENT_SECRET),
                    config.getValueAsBoolean(ServerConfig.OAUTH_VALIDATE_COMMON_CHECKS, true),
                    config.getValue(ServerConfig.OAUTH_VALIDATE_AUDIENCE)
            );
        }

        usernameClaim = config.getValue(Config.OAUTH_USERNAME_CLAIM, "sub");
        if ("sub".equals(usernameClaim)) {
            usernameClaim = null;
        }
    }

    private void validateConfig() {
        String jwksUri = config.getValue(ServerConfig.OAUTH_JWKS_ENDPOINT_URI);
        String introspectUri = config.getValue(ServerConfig.OAUTH_INTROSPECTION_ENDPOINT_URI);

        // if both set or none set
        if ((jwksUri == null) == (introspectUri == null)) {
            throw new RuntimeException("OAuth validator configuration error - one of the two should be specified: OAUTH_JWKS_ENDPOINT_URI (for fast local signature validation) or OAUTH_INTROSPECTION_ENDPOINT_URI (for using authorization server during validation)");
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof OAuthBearerValidatorCallback) {
                handleCallback((OAuthBearerValidatorCallback) callback);
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    private void handleCallback(OAuthBearerValidatorCallback callback) throws IOException {
        if (callback.tokenValue() == null) {
            throw new IllegalArgumentException("Callback has null token value!");
        }

        String token = callback.tokenValue();

        debugLogToken(token);

        try {
            TokenInfo ti = validateToken(token);

            callback.token(new OAuthBearerToken() {

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
                    if (usernameClaim != null) {
                        if (ti.payload() != null) {
                            return getClaimFromJWT(usernameClaim, ti.payload());
                        } else {
                            throw new IllegalStateException("Username claim extraction not supported by validator: " + validator.getClass());
                        }
                    }
                    return ti.subject();
                }

                @Override
                public Long startTimeMs() {
                    return ti.issuedAtMs();
                }
            });

        } catch (TokenValidationException e) {
            log.warn("Validation failed for token: " + token, e);
            callback.error(e.status(), null, null);
        } catch (RuntimeException e) {
            throw new AuthenticationException("Validation failed due to runtime exception:", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected failure during signature check:", e);
        }
    }

    private TokenInfo validateToken(String token) {
        return validator.validate(token);
    }


    private void debugLogToken(String token) {
        JWSInput parser;
        try {
            parser = new JWSInput(token);
            log.info("Token: " + parser.readContentAsString());
        } catch (JWSInputException e) {
            log.info("Token doesn't seem to be JWT token: " + token, e);
            return;
        }

        try {
            AccessToken t = parser.readJsonContent(AccessToken.class);
            log.info("Access token expires at (UTC): " + LocalDateTime.ofEpochSecond(t.getExpiration(), 0, ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME));
        } catch (JWSInputException e) {
            // Try parse as refresh token:
            log.info("[IGNORED] Failed to parse JWT token's payload", e);
        }
    }
}
