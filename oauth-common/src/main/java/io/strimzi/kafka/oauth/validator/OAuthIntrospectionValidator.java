/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.fasterxml.jackson.databind.JsonNode;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.jsonpath.JsonPathFilterQuery;
import org.apache.kafka.common.utils.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import static io.strimzi.kafka.oauth.common.HttpUtil.post;
import static io.strimzi.kafka.oauth.common.HttpUtil.get;
import static io.strimzi.kafka.oauth.common.LogUtil.mask;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.base64encode;
import static io.strimzi.kafka.oauth.validator.TokenValidationException.Status;

/**
 * This class is responsible for validating the token during session authentication by using an introspection endpoint.
 * <p>
 * It works by sending the token to the configured authorization server's introspection endpoint.
 * The endpoint returns a response with whether the token is valid or not, and it usually also returns additional attributes, that
 * can be used to enforce additional constraints, and prevent some otherwise valid tokens from authenticating.
 * </p>
 */
public class OAuthIntrospectionValidator implements TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(OAuthIntrospectionValidator.class);

    private final URI introspectionURI;
    private final String validIssuerURI;
    private final URI userInfoURI;
    private final String validTokenType;
    private final String clientId;
    private final String clientSecret;
    private final String audience;
    private final JsonPathFilterQuery customClaimMatcher;
    private final SSLSocketFactory socketFactory;
    private final HostnameVerifier hostnameVerifier;
    private final PrincipalExtractor principalExtractor;

    /**
     * Create a new instance.
     *
     * @param introspectionEndpointUri The introspection endpoint url at the authorization server
     * @param socketFactory The optional SSL socket factory to use when establishing the connection to authorization server
     * @param verifier The optional hostname verifier used to validate the TLS certificate by the authorization server
     * @param principalExtractor The object used to extract the username from the attributes in the server's response
     * @param issuerUri The required value of the 'iss' claim in JWT token
     * @param userInfoUri The optional user info endpoint url at the authorization server, used as a failover when user id can't be extracted from the introspection endpoint response
     * @param validTokenType The optional token type enforcement - only the specified token type is accepted as valid
     * @param clientId The clientId of the OAuth2 client representing this Kafka broker - needed to authenticate to the introspection endpoint
     * @param clientSecret The secret of the OAuth2 client representing this Kafka broker - needed to authenticate to the introspection endpoint
     * @param audience The optional audience check. If specified, the 'aud' attributie of the introspection endpoint response needs to contain the configured clientId
     * @param customClaimCheck The optional JSONPath filter query for additional custom attribute checking
     */
    public OAuthIntrospectionValidator(String introspectionEndpointUri,
                                       SSLSocketFactory socketFactory,
                                       HostnameVerifier verifier,
                                       PrincipalExtractor principalExtractor,
                                       String issuerUri,
                                       String userInfoUri,
                                       String validTokenType,
                                       String clientId,
                                       String clientSecret,
                                       String audience,
                                       String customClaimCheck) {

        if (introspectionEndpointUri == null) {
            throw new IllegalArgumentException("introspectionEndpointUri == null");
        }

        try {
            this.introspectionURI = new URI(introspectionEndpointUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid introspection endpoint uri: " + introspectionEndpointUri, e);
        }

        if (socketFactory != null && !"https".equals(introspectionURI.getScheme())) {
            throw new IllegalArgumentException("SSL socket factory set but introspectionEndpointUri not 'https'");
        }
        this.socketFactory = socketFactory;

        if (verifier != null && !"https".equals(introspectionURI.getScheme())) {
            throw new IllegalArgumentException("Certificate hostname verifier set but keysEndpointUri not 'https'");
        }
        this.hostnameVerifier = verifier;

        this.principalExtractor = principalExtractor != null ? principalExtractor : new PrincipalExtractor();

        if (issuerUri != null) {
            try {
                new URI(issuerUri);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid issuer uri: " + issuerUri, e);
            }
        }
        this.validIssuerURI = issuerUri;

        if (userInfoUri != null) {
            try {
                this.userInfoURI = new URI(userInfoUri);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid userInfo uri: " + userInfoUri, e);
            }
        } else {
            this.userInfoURI = null;
        }

        this.validTokenType = validTokenType;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
        this.customClaimMatcher = parseCustomClaimCheck(customClaimCheck);

        if (log.isDebugEnabled()) {
            log.debug("Configured OAuthIntrospectionValidator:\n    introspectionEndpointUri: " + introspectionURI
                    + "\n    sslSocketFactory: " + socketFactory
                    + "\n    hostnameVerifier: " + hostnameVerifier
                    + "\n    principalExtractor: " + principalExtractor
                    + "\n    validIssuerUri: " + validIssuerURI
                    + "\n    userInfoUri: " + userInfoURI
                    + "\n    validTokenType: " + validTokenType
                    + "\n    clientId: " + clientId
                    + "\n    clientSecret: " + mask(clientSecret)
                    + "\n    audience: " + audience
                    + "\n    customClaimCheck: " + customClaimCheck
            );
        }
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

    @SuppressWarnings("checkstyle:NPathComplexity")
    public TokenInfo validate(String token) {

        String authorization = clientSecret != null ?
                "Basic " + base64encode(clientId + ':' + clientSecret) :
                null;

        StringBuilder body = new StringBuilder("token=").append(token);

        JsonNode response;
        try {
            response = post(introspectionURI, socketFactory, hostnameVerifier, authorization,
                    "application/x-www-form-urlencoded", body.toString(), JsonNode.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to introspect token - send, fetch or parse failed: ", e);
        }

        boolean active;
        try {
            active = response.get("active").asBoolean();
        } catch (Exception e) {
            throw new RuntimeException("Failed to introspect token - invalid response: \"active\" attribute is missing or not a boolean (" + response.get("active") + ")");
        }

        if (!active) {
            throw new TokenExpiredException("Token has expired");
        }

        JsonNode value = null;

        value = response.get("exp");
        if (value == null) {
            throw new IllegalStateException("Introspection response contains no expires information (\"exp\"): " + response);
        }
        long expiresMillis = 1000 * value.asLong();
        if (Time.SYSTEM.milliseconds() > expiresMillis)    {
            throw new TokenExpiredException("The token expired at: " + expiresMillis + " (" +
                    TimeUtil.formatIsoDateTimeUTC(expiresMillis) + ")");
        }

        value = response.get("iat");
        long iat = value == null ? 0 : 1000 * value.asLong();

        String principal = principalExtractor.getPrincipal(response);
        if (principal == null) {
            if (userInfoURI != null) {
                principal = getPrincipalFromUserInfoEndpoint(token);
            }
            if (principal == null && !principalExtractor.isConfigured()) {
                principal = principalExtractor.getSub(response);
            }
            if (principal == null) {
                throw new RuntimeException("Failed to extract principal - check usernameClaim, fallbackUsernameClaim configuration");
            }
        }
        performOptionalChecks(response);

        value = response.get("scope");
        String scopes = value != null ? String.join(" ", JSONUtil.asListOfString(value)) : null;

        return new TokenInfo(token, scopes, principal, iat, expiresMillis);
    }

    String getPrincipalFromUserInfoEndpoint(String token) {
        String authorization = "Bearer " + token;
        JsonNode response;
        try {
            response = get(userInfoURI, socketFactory, hostnameVerifier, authorization, JsonNode.class);
        } catch (IOException e) {
            throw new RuntimeException("Request to User Info Endpoint failed: ", e);
        }
        // apply principalExtractor
        String principal = principalExtractor.getPrincipal(response);

        if (principal == null && !principalExtractor.isConfigured()) {
            principal = principalExtractor.getSub(response);
        }
        return principal;
    }

    private void performOptionalChecks(JsonNode response) {
        JsonNode value;
        if (validIssuerURI != null) {
            value = response.get("iss");
            if (value == null || !validIssuerURI.equals(value.asText())) {
                throw new TokenValidationException("Token check failed - Invalid issuer: " + value)
                        .status(Status.INVALID_TOKEN);
            }
        }

        if (validTokenType != null) {
            value = response.get("token_type");
            if (value == null || !validTokenType.equals(value.asText())) {
                throw new TokenValidationException("Token check failed - Invalid token type: " + value + " (should be '" + validTokenType + "')" + (value == null ? ". Consider not setting OAUTH_VALID_TOKEN_TYPE." : ""))
                        .status(Status.UNSUPPORTED_TOKEN_TYPE);
            }
        }

        if (audience != null) {
            value = response.get("aud");
            List<String> audienceList = value != null ? JSONUtil.asListOfString(value) : Collections.emptyList();
            if (!audienceList.contains(audience)) {
                throw new TokenValidationException("Token check failed - Invalid audience: " + value)
                        .status(Status.INVALID_TOKEN);
            }
        }

        if (customClaimMatcher != null) {
            if (!customClaimMatcher.matches(response)) {
                throw new TokenValidationException("Token check failed - Custom claim check failed.")
                        .status(Status.INVALID_TOKEN);
            }
        }
    }
}
