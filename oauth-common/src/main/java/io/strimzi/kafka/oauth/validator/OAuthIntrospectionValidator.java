/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.PrincipalExtractor;
import io.strimzi.kafka.oauth.common.TimeUtil;
import io.strimzi.kafka.oauth.common.TokenInfo;
import io.strimzi.kafka.oauth.jsonpath.JsonPathFilterQuery;
import io.strimzi.kafka.oauth.jsonpath.JsonPathQuery;
import io.strimzi.kafka.oauth.metrics.IntrospectHttpSensorKeyProducer;
import io.strimzi.kafka.oauth.metrics.SensorKeyProducer;
import io.strimzi.kafka.oauth.metrics.UserInfoHttpSensorKeyProducer;
import io.strimzi.kafka.oauth.services.OAuthMetrics;
import io.strimzi.kafka.oauth.services.Services;
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
import java.util.Set;
import java.util.stream.Collectors;

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

    private final String validatorId;
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
    private final JsonPathQuery groupsMatcher;
    private final String groupsDelimiter;

    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;

    private final boolean enableMetrics;
    private final OAuthMetrics metrics;
    private final SensorKeyProducer introspectHttpSensorKeyProducer;
    private final SensorKeyProducer userInfoHttpSensorKeyProducer;

    /**
     * Create a new instance.
     *
     * @param id A unique id to associate with this validator for the purpose of validator lifecycle and metrics tracking
     * @param introspectionEndpointUri The introspection endpoint url at the authorization server
     * @param socketFactory The optional SSL socket factory to use when establishing the connection to authorization server
     * @param verifier The optional hostname verifier used to validate the TLS certificate by the authorization server
     * @param principalExtractor The object used to extract the username from the attributes in the server's response
     * @param groupsClaimQuery The JsonPath query for extracting groups from introspection endpoint response
     * @param groupsClaimDelimiter The delimiter used to parse groups from the result of applying <em>groupQuery</em> to what introspection endpoint returns
     * @param issuerUri The required value of the 'iss' claim in the introspection endpoint response
     * @param userInfoUri The optional user info endpoint url at the authorization server, used as a failover when user id can't be extracted from the introspection endpoint response
     * @param validTokenType The optional token type enforcement - only the specified token type is accepted as valid
     * @param clientId The clientId of the OAuth2 client representing this Kafka broker - needed to authenticate to the introspection endpoint
     * @param clientSecret The secret of the OAuth2 client representing this Kafka broker - needed to authenticate to the introspection endpoint
     * @param audience The optional audience check. If specified, the 'aud' attribute of the introspection endpoint response needs to contain the configured clientId
     * @param customClaimCheck The optional JSONPath filter query for additional custom attribute checking
     * @param connectTimeoutSeconds The maximum time to wait for connection to authorization server to be established (in seconds)
     * @param readTimeoutSeconds The maximum time to wait for response from authorization server after connection has been established and request sent (in seconds)
     * @param enableMetrics The switch that enables metrics collection
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public OAuthIntrospectionValidator(String id,
                                       String introspectionEndpointUri,
                                       SSLSocketFactory socketFactory,
                                       HostnameVerifier verifier,
                                       PrincipalExtractor principalExtractor,
                                       String groupsClaimQuery,
                                       String groupsClaimDelimiter,
                                       String issuerUri,
                                       String userInfoUri,
                                       String validTokenType,
                                       String clientId,
                                       String clientSecret,
                                       String audience,
                                       String customClaimCheck,
                                       int connectTimeoutSeconds,
                                       int readTimeoutSeconds,
                                       boolean enableMetrics) {

        this.validatorId = checkValidatorId(id);

        this.introspectionURI = checkIntrospectionUri(introspectionEndpointUri);

        this.socketFactory = checkSocketFactory(socketFactory);

        this.hostnameVerifier = checkHostnameVerifier(verifier);

        this.principalExtractor = principalExtractor != null ? principalExtractor : new PrincipalExtractor();

        this.groupsMatcher = parseGroupsQuery(groupsClaimQuery);
        this.groupsDelimiter = parseGroupsDelimiter(groupsClaimDelimiter);

        this.validIssuerURI = checkIssuerUri(issuerUri);

        this.userInfoURI = checkUserInfoUri(userInfoUri);

        this.validTokenType = validTokenType;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
        this.customClaimMatcher = parseCustomClaimCheck(customClaimCheck);

        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;

        this.enableMetrics = enableMetrics;
        metrics = enableMetrics ? Services.getInstance().getMetrics() : null;

        introspectHttpSensorKeyProducer = new IntrospectHttpSensorKeyProducer(validatorId, introspectionURI);
        userInfoHttpSensorKeyProducer = userInfoURI != null ? new UserInfoHttpSensorKeyProducer(validatorId, userInfoURI) : null;

        if (log.isDebugEnabled()) {
            log.debug("Configured OAuthIntrospectionValidator:"
                    + "\n    id: " + id
                    + "\n    introspectionEndpointUri: " + introspectionURI
                    + "\n    sslSocketFactory: " + socketFactory
                    + "\n    hostnameVerifier: " + hostnameVerifier
                    + "\n    principalExtractor: " + principalExtractor
                    + "\n    groupsClaimQuery: " + groupsClaimQuery
                    + "\n    groupsClaimDelimiter: " + groupsClaimDelimiter
                    + "\n    validIssuerUri: " + validIssuerURI
                    + "\n    userInfoUri: " + userInfoURI
                    + "\n    validTokenType: " + validTokenType
                    + "\n    clientId: " + clientId
                    + "\n    clientSecret: " + mask(clientSecret)
                    + "\n    audience: " + audience
                    + "\n    customClaimCheck: " + customClaimCheck
                    + "\n    connectTimeoutSeconds: " + connectTimeoutSeconds
                    + "\n    readTimeoutSeconds: " + readTimeoutSeconds
                    + "\n    enableMetrics: " + enableMetrics
            );
        }
    }

    private HostnameVerifier checkHostnameVerifier(HostnameVerifier verifier) {
        if (verifier != null && !"https".equals(introspectionURI.getScheme())) {
            throw new IllegalArgumentException("Certificate hostname verifier set but keysEndpointUri not 'https'");
        }
        return verifier;
    }

    private URI checkUserInfoUri(String userInfoUri) {
        if (userInfoUri != null) {
            try {
                return new URI(userInfoUri);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid userInfo uri: " + userInfoUri, e);
            }
        }
        return null;
    }

    private String checkIssuerUri(String issuerUri) {
        if (issuerUri != null) {
            try {
                new URI(issuerUri);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid issuer uri: " + issuerUri, e);
            }
        }
        return issuerUri;
    }

    private SSLSocketFactory checkSocketFactory(SSLSocketFactory socketFactory) {
        if (socketFactory != null && !"https".equals(introspectionURI.getScheme())) {
            throw new IllegalArgumentException("SSL socket factory set but introspectionEndpointUri not 'https'");
        }
        return socketFactory;
    }

    private URI checkIntrospectionUri(String introspectionEndpointUri) {
        if (introspectionEndpointUri == null) {
            throw new IllegalArgumentException("introspectionEndpointUri == null");
        }

        try {
            return new URI(introspectionEndpointUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid introspection endpoint uri: " + introspectionEndpointUri, e);
        }
    }

    private String checkValidatorId(String validatorId) {
        if (validatorId == null) {
            throw new IllegalArgumentException("validatorId == null");
        }
        return validatorId;
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

    @SuppressWarnings("checkstyle:NPathComplexity")
    public TokenInfo validate(String token) {

        long requestStartTime = System.currentTimeMillis();

        String authorization = clientSecret != null ?
                "Basic " + base64encode(clientId + ':' + clientSecret) :
                null;

        StringBuilder body = new StringBuilder("token=").append(token);

        JsonNode response;
        try {
            response = post(introspectionURI, socketFactory, hostnameVerifier, authorization,
                    "application/x-www-form-urlencoded", body.toString(), JsonNode.class, connectTimeoutSeconds, readTimeoutSeconds);

            addIntrospectHttpMetricSuccessTime(requestStartTime);

        } catch (IOException e) {
            addIntrospectHttpMetricErrorTime(e, requestStartTime);
            throw new ValidationException("Failed to introspect token - send, fetch or parse failed: ", e);
        }

        JsonNode activeAttr = response.get("active");
        if (!(activeAttr instanceof BooleanNode)) {
            throw new ValidationException("Failed to introspect token - invalid response: \"active\" attribute is missing or not a boolean (" + activeAttr + ")");
        }
        boolean active = activeAttr.asBoolean();
        if (!active) {
            throw new TokenValidationException("Token validation failed: Token not active");
        }

        JsonNode value;

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
        JsonNode fallbackResponse = null;
        if (principal == null) {
            if (userInfoURI != null) {
                fallbackResponse = getUserInfoEndpointResponse(token);
                principal = getPrincipalFromUserInfoEndpoint(fallbackResponse);
            }
            if (principal == null && !principalExtractor.isConfigured()) {
                principal = principalExtractor.getSub(response);
            }
            if (principal == null) {
                throw new ValidationException("Failed to extract principal - check usernameClaim, fallbackUsernameClaim configuration");
            }
        }
        performOptionalChecks(response);
        Set<String> groups = null;
        if (groupsMatcher != null) {
            groups = extractGroupsFromResponse(response);

            if (groups == null && fallbackResponse != null) {
                groups = extractGroupsFromResponse(fallbackResponse);
            }
        }

        value = response.get("scope");
        String scopes = value != null ? String.join(" ", JSONUtil.asListOfString(value)) : null;

        return new TokenInfo(token, scopes, principal, groups, iat, expiresMillis);
    }

    private Set<String> extractGroupsFromResponse(JsonNode userInfoJson) {
        JsonNode result = groupsMatcher.apply(userInfoJson);
        if (result == null) {
            return null;
        }
        List<String> groups = JSONUtil.asListOfString(result, groupsDelimiter != null ? groupsDelimiter : ",");

        // sanitize the result
        return groups.stream().map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toSet());
    }

    private JsonNode getUserInfoEndpointResponse(String token) {
        String authorization = "Bearer " + token;
        JsonNode response;

        long requestStartTime = System.currentTimeMillis();
        try {
            response = get(userInfoURI, socketFactory, hostnameVerifier, authorization, JsonNode.class, connectTimeoutSeconds, readTimeoutSeconds);

            addUserInfoHttpMetricSuccessTime(requestStartTime);

        } catch (IOException e) {
            addUserInfoHttpMetricErrorTime(e, requestStartTime);
            throw new ValidationException("Request to User Info Endpoint failed: ", e);
        }

        return response;
    }

    private String getPrincipalFromUserInfoEndpoint(JsonNode userInfoJson) {
        // apply principalExtractor
        String principal = principalExtractor.getPrincipal(userInfoJson);

        if (principal == null && !principalExtractor.isConfigured()) {
            principal = principalExtractor.getSub(userInfoJson);
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

    @Override
    public String getValidatorId() {
        return validatorId;
    }

    private void addIntrospectHttpMetricSuccessTime(long startTime) {
        if (enableMetrics) {
            metrics.addTime(introspectHttpSensorKeyProducer.successKey(), System.currentTimeMillis() - startTime);
        }
    }

    private void addIntrospectHttpMetricErrorTime(Throwable e, long startTime) {
        if (enableMetrics) {
            metrics.addTime(introspectHttpSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTime);
        }
    }

    private void addUserInfoHttpMetricSuccessTime(long startTime) {
        if (enableMetrics) {
            metrics.addTime(userInfoHttpSensorKeyProducer.successKey(), System.currentTimeMillis() - startTime);
        }
    }

    private void addUserInfoHttpMetricErrorTime(Throwable e, long startTime) {
        if (enableMetrics) {
            metrics.addTime(userInfoHttpSensorKeyProducer.errorKey(e), System.currentTimeMillis() - startTime);
        }
    }
}
