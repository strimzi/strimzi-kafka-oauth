/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSObject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.strimzi.kafka.oauth.common.JSONUtil;
import io.strimzi.kafka.oauth.common.NimbusPayloadTransformer;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.function.Consumer;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static io.strimzi.kafka.oauth.common.IOUtil.randomHexString;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.base64decode;
import static io.strimzi.kafka.oauth.common.TokenInfo.EXP;
import static io.strimzi.kafka.oauth.common.TokenInfo.ISS;
import static io.strimzi.testsuite.oauth.server.Commons.handleFailure;
import static io.strimzi.testsuite.oauth.server.Commons.isOneOf;
import static io.strimzi.testsuite.oauth.server.Commons.sendResponse;
import static io.strimzi.testsuite.oauth.server.Commons.setContextLog;
import static io.strimzi.testsuite.oauth.server.Endpoint.FAILING_GRANTS;
import static io.strimzi.testsuite.oauth.server.Endpoint.FAILING_INTROSPECT;
import static io.strimzi.testsuite.oauth.server.Endpoint.FAILING_TOKEN;
import static io.strimzi.testsuite.oauth.server.Endpoint.FAILING_USERINFO;
import static io.strimzi.testsuite.oauth.server.Endpoint.GRANTS;
import static io.strimzi.testsuite.oauth.server.Endpoint.INTROSPECT;
import static io.strimzi.testsuite.oauth.server.Endpoint.TOKEN;
import static io.strimzi.testsuite.oauth.server.Endpoint.USERINFO;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_200;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_200_DELAYED;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_FAILING_500;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_JWKS_RSA_WITHOUT_SIG_USE;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_JWKS_RSA_WITH_SIG_USE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;

public class AuthServerRequestHandler implements Handler<HttpServerRequest> {

    private static final Logger log = LoggerFactory.getLogger("oauth");
    private static final NimbusPayloadTransformer TRANSFORMER = new NimbusPayloadTransformer();

    private static final int EXPIRES_IN_SECONDS = 600;

    private final MockOAuthServerMainVerticle verticle;

    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public AuthServerRequestHandler(MockOAuthServerMainVerticle verticle) {
        this.verticle = verticle;
    }

    @Override
    public void handle(HttpServerRequest req) {
        log.info("> " + req.method().name() + " " + req.path());
        setContextLog(log);

        if (!isOneOf(req.method(), GET, POST)) {
            sendResponse(req, METHOD_NOT_ALLOWED);
            return;
        }

        String[] path = req.path().split("/");

        if (path.length != 2) {
            sendResponse(req, NOT_FOUND);
            return;
        }

        Endpoint endpoint = Endpoint.fromString(path[1]);
        Mode mode = verticle.getMode(endpoint);

        try {
            if (!processRequest(endpoint, mode, req)) {
                if (!generateResponse(req, mode)) {
                    sendResponse(req, OK, "" + verticle.getMode(endpoint));
                }
            }
        } catch (Throwable t) {
            handleFailure(req, t, log);
        }
    }

    private boolean processRequest(Endpoint endpoint, Mode mode, HttpServerRequest req) throws NoSuchAlgorithmException, JOSEException, InterruptedException {
        if (endpoint == Endpoint.JWKS &&
                isOneOf(mode, MODE_200, MODE_JWKS_RSA_WITH_SIG_USE, MODE_JWKS_RSA_WITHOUT_SIG_USE)) {
            processJwksRequest(req, mode);
        } else if (endpoint == TOKEN && mode == MODE_200) {
            processTokenRequest(req);
        } else if (endpoint == FAILING_TOKEN) {
            processFailingRequest(req, endpoint, mode, this::processTokenRequest);
        } else if (endpoint == INTROSPECT && mode == MODE_200) {
            processIntrospectRequest(req);
        } else if (endpoint == FAILING_INTROSPECT) {
            processFailingRequest(req, endpoint, mode, this::processIntrospectRequest);
        } else if (endpoint == USERINFO && mode == MODE_200) {
            processUserInfoRequest(req);
        } else if (endpoint == FAILING_USERINFO) {
            processFailingRequest(req, endpoint, mode, this::processUserInfoRequest);
        } else if (endpoint == GRANTS && (mode == MODE_200 || mode == MODE_200_DELAYED)) {
            if (mode == MODE_200_DELAYED) {
                //verticle.getVertx().setTimer(1000, v -> processGrantsRequest(req));
                Thread.sleep(2000);

                processGrantsRequest(req);
            } else {
                processGrantsRequest(req);
            }
        } else if (endpoint == FAILING_GRANTS) {
            processFailingRequest(req, endpoint, mode, this::processGrantsRequest);
        } else {
            return false;
        }

        return true;
    }

    private static boolean generateResponse(HttpServerRequest req, Mode mode) {
        boolean result = true;
        switch (mode) {
            case MODE_STALL:
                // don't send response
                // client will timeout on their side
                break;
            case MODE_400:
                sendResponse(req, BAD_REQUEST);
                break;
            case MODE_401:
                sendResponse(req, UNAUTHORIZED);
                break;
            case MODE_403:
                sendResponse(req, FORBIDDEN);
                break;
            case MODE_404:
                sendResponse(req, NOT_FOUND);
                break;
            case MODE_500:
                sendResponse(req, INTERNAL_SERVER_ERROR);
                break;
            case MODE_503:
                sendResponse(req, SERVICE_UNAVAILABLE);
                break;
            default:
                result = false;
                log.error("Unexpected mode: " + mode);
        }
        if (result) {
            log.info("Returned mode status: " + mode);
        }
        return result;
    }

    private void processGrantsRequest(HttpServerRequest req) {
        if (req.method() != POST) {
            sendResponse(req, METHOD_NOT_ALLOWED);
            return;
        }

        // Need to read the complete body before we can get the form attributes
        req.setExpectMultipart(true);
        req.endHandler(v -> {

            MultiMap form = req.formAttributes();
            log.info(form.toString());

            String grantType = form.get("grant_type");
            if (grantType == null) {
                sendResponse(req, BAD_REQUEST);
                return;
            }

            if (!"urn:ietf:params:oauth:grant-type:uma-ticket".equals(grantType)) {
                sendResponse(req, BAD_REQUEST);
                return;
            }

            String authorization = req.headers().get("Authorization");

            if (!authorization.startsWith("Bearer ")) {
                sendResponse(req, UNAUTHORIZED);
                return;
            }

            String token = authorization.substring(7);

            // Let's take the info from the token itself
            try {
                JWSObject.parse(token);
            } catch (Exception e) {
                log.error("Failed to parse the token: ", e);
                sendResponse(req, UNAUTHORIZED);
                return;
            }

            try {
                // Create JSON response
                JsonArray result = verticle.getGrants().get(token);
                if (result == null) {
                    String jsonString = new JsonArray(
                            "[{\"scopes\":[\"Delete\",\"Write\",\"Describe\",\"Read\",\"Alter\",\"Create\",\"DescribeConfigs\",\"AlterConfigs\"],\"rsid\":\"ca6f195f-dbdc-48b7-a953-8e441d17f7fa\",\"rsname\":\"Topic:*\"}," +
                                    "{\"scopes\":[\"IdempotentWrite\"],\"rsid\":\"73af36e6-5796-43e7-8129-b57fe0bac7a1\",\"rsname\":\"Cluster:*\"}," +
                                    "{\"scopes\":[\"Describe\",\"Read\"],\"rsid\":\"141c56e8-1a85-40f3-b38a-f490bad76913\",\"rsname\":\"Group:*\"}]")
                            .encode();
                    sendResponse(req, OK, jsonString);

                } else {
                    String jsonString = result.encode();
                    if ("[]".equals(jsonString)) {
                        sendResponse(req, UNAUTHORIZED);
                    } else {
                        sendResponse(req, OK, jsonString);
                    }
                }
            } catch (Throwable t) {
                handleFailure(req, t, log);
            }
        });
    }

    private void processTokenRequest(HttpServerRequest req) {
        if (req.method() != POST) {
            sendResponse(req, METHOD_NOT_ALLOWED);
            return;
        }

        // Need to read the complete body before we can get the form attributes
        req.setExpectMultipart(true);
        req.endHandler(v -> {

            MultiMap form = req.formAttributes();
            log.info(form.toString());

            // grant_type is required
            String grantType = form.get("grant_type");
            if (grantType == null) {
                sendResponse(req, BAD_REQUEST);
                return;
            }

            String authorization = req.headers().get("Authorization");

            String username = null;

            // clientId should be passed via Authorization header
            // and should have a secret matching the stored client record
            String clientId = authorizeClient(authorization);

            // or via the body together with its assertion
            // and should match client assertion in the stored client record and assertion type
            if (clientId == null) {
                final String clientIdFromForm = form.get("client_id");
                final String clientAssertion = form.get("client_assertion");
                final String clientAssertionType = form.get("client_assertion_type");
                clientId = authorizeClientUsingAssertion(clientIdFromForm, clientAssertion, clientAssertionType);
            }

            if ("password".equals(grantType)) {
                // if it's a password auth, also make sure the username and password are a match
                username = authorizeUser(form.get("username"), form.get("password"));
                if (username != null && clientId == null) {
                    clientId = form.get("client_id");
                }
            } else if ("refresh_token".equals(grantType)) {
                // if it's a refresh_token auth, make sure the refresh_token has been issued by us
                // and that clientId matches (if clientId not available at this point
                RefreshTokenInfo refreshTokenInfo = verticle.getRefreshTokens().get(form.get("refresh_token"));
                if (clientId == null) {
                    clientId = form.get("client_id");
                }
                if (processUnauthorizedRefreshToken(req, clientId, refreshTokenInfo)) {
                    return;
                }
                username = refreshTokenInfo.username;
            }

            if (processUnauthorized(req, grantType, username, clientId)) {
                return;
            }

            try {
                // Create a signed JWT token
                UserInfo userInfo = username != null ? verticle.getUsers().get(username) : null;
                long expiresIn = userInfo != null && userInfo.expiresIn != null ? userInfo.expiresIn : EXPIRES_IN_SECONDS;

                String accessToken = createSignedAccessToken(clientId, username, expiresIn);
                String refreshToken = createRefreshToken(clientId, username);
                JsonObject result = new JsonObject();
                result.put("access_token", accessToken);
                result.put("refresh_token", refreshToken);
                result.put("expires_in", expiresIn);
                result.put("scope", "all");

                String jsonString = result.encode();
                sendResponse(req, OK, jsonString);

            } catch (Throwable t) {
                handleFailure(req, t, log);
            }
        });
    }

    private boolean processUnauthorized(HttpServerRequest req, String grantType, String username, String clientId) {
        if (!supportedGrantType(grantType, clientId)) {
            sendResponse(req, UNAUTHORIZED);
            return true;
        }

        if ("password".equals(grantType) && username == null) {
            sendResponse(req, UNAUTHORIZED);
            return true;
        }
        return false;
    }

    private static boolean processUnauthorizedRefreshToken(HttpServerRequest req, String clientId, RefreshTokenInfo refreshTokenInfo) {
        if (refreshTokenInfo == null) {
            sendResponse(req, UNAUTHORIZED);
            return true;
        } else {
            if (!refreshTokenInfo.clientId.equals(clientId)) {
                sendResponse(req, UNAUTHORIZED);
                return true;
            }
        }
        return false;
    }

    private boolean supportedGrantType(String grantType, String clientId) {
        return clientId != null &&
                ("client_credentials".equals(grantType) || "password".equals(grantType) || "refresh_token".equals(grantType));
    }

    private void processIntrospectRequest(HttpServerRequest req) {
        if (req.method() != POST) {
            sendResponse(req, METHOD_NOT_ALLOWED);
            return;
        }

        // Need to read the complete body before we can get the form attributes
        req.setExpectMultipart(true);
        req.endHandler(v -> {

            MultiMap form = req.formAttributes();
            log.info(form.toString());

            String token = form.get("token");
            if (token == null) {
                sendResponse(req, BAD_REQUEST);
                return;
            }

            String authorization = req.headers().get("Authorization");
            String clientId = authorizeClient(authorization);

            if (clientId == null) {
                sendResponse(req, UNAUTHORIZED);
                return;
            }

            // Let's take the info from the token itself
            JWSObject jws;
            try {
                jws = JWSObject.parse(token);
            } catch (Exception e) {
                log.error("Failed to parse the token: ", e);
                sendResponse(req, OK, new JsonObject().put("active", false).encode());
                return;
            }

            try {
                JsonNode parsed = jws.getPayload().toType(TRANSFORMER);

                // Create JSON response
                JsonObject result = new JsonObject();

                // token is active if not in the revocation list, if issued by us and if current date is less than expiry
                JsonNode node = parsed.get(ISS);
                JsonNode expNode = parsed.get(EXP);

                result.put("active", node != null && "https://mockoauth:8090".equals(node.asText())
                        && expNode != null && !isExpired(expNode.asInt()) && !isRevoked(token));

                if (node != null) {
                    result.put(ISS, node.asText());
                }
                result.put("scope", "all");

                node = parsed.get("clientId");
                result.put("client_id", node.asText());

                node = parsed.get("username");
                if (node != null) {
                    result.put("username", node.asText());
                }

                if (expNode != null) {
                    result.put(EXP, expNode.asInt());
                }

                String jsonString = result.encode();
                sendResponse(req, OK, jsonString);

            } catch (Throwable t) {
                handleFailure(req, t, log);
            }
        });
    }

    private void processFailingRequest(HttpServerRequest req, Endpoint endpoint, Mode mode, Consumer<HttpServerRequest> requestCompletingFunction) {

        // Always fail without a coinflip if this mode is used
        if (mode == MODE_FAILING_500) {
            sendResponse(req, INTERNAL_SERVER_ERROR);
            return;
        }

        // make every other request fail with the error indicated by mode
        if (verticle.flipCoin(endpoint)) {
            // fail
            if (generateResponse(req, mode)) return;
        }
        requestCompletingFunction.accept(req);
    }

    private void processUserInfoRequest(HttpServerRequest req) {
        if (req.method() != GET) {
            sendResponse(req, METHOD_NOT_ALLOWED);
            return;
        }

        String authorization = req.headers().get("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            sendResponse(req, BAD_REQUEST);
            return;
        }

        String token = authorization.substring(7);
        if (token.length() == 0) {
            sendResponse(req, BAD_REQUEST);
            return;
        }

        // Let's take the info from the token itself
        JWSObject jws;
        try {
            jws = JWSObject.parse(token);
        } catch (Exception e) {
            log.error("Failed to parse the token: ", e);
            sendResponse(req, OK, new JsonObject().put("active", false).encode());
            return;
        }

        try {
            JsonNode parsed = jws.getPayload().toType(TRANSFORMER);

            // Create JSON response
            JsonObject result = new JsonObject();

            JsonNode node = parsed.get("clientId");
            String uid = node.asText();

            node = parsed.get("username");
            if (node != null) {
                uid = node.asText();
                result.put("username", uid);
            }

            if (uid != null) {
                result.put("uid", uid);
            }

            String jsonString = result.encode();
            sendResponse(req, OK, jsonString);

        } catch (Throwable t) {
            handleFailure(req, t, log);
        }
    }

    private boolean isRevoked(String token) {
        return verticle.getRevokedTokens().contains(token);
    }

    private boolean isExpired(int expiryTimeSeconds) {
        // is expiry in the past
        return System.currentTimeMillis() > expiryTimeSeconds * 1000L;
    }

    private String createSignedAccessToken(String clientId, String username, long expiresIn) throws JOSEException, NoSuchAlgorithmException {

        // Create RSA-signer with the private key
        JWSSigner signer = new RSASSASigner(verticle.getSigKey());

        // Prepare JWT with claims set
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(username != null ? username : clientId)
                .issuer("https://mockoauth:8090")
                .expirationTime(new Date(System.currentTimeMillis() + expiresIn * 1000));

        if (clientId != null) {
            builder.claim("clientId", clientId);
        }
        if (username != null) {
            builder.claim("username", username);
        }
        JWTClaimsSet claimsSet = builder.build();
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(verticle.getSigKey().getKeyID()).build(),
                claimsSet);

        // Compute the RSA signature
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    /**
     * This method creates opaque refresh tokens that never expire
     *
     * @param clientId clientId for the client
     * @param username username of the user
     * @return A refresh token
     */
    private String createRefreshToken(String clientId, String username) {
        // create a random string and store it
        String refreshToken = randomHexString(32);

        // This is essentially a memory leak as refresh tokens are only ever added, never removed
        verticle.createRefreshToken(refreshToken, new RefreshTokenInfo(clientId, username));
        return refreshToken;
    }

    private String authorizeClient(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return null;
        }
        String decoded = base64decode(authorization.substring(6));
        int pos = decoded.indexOf(":");
        if (pos == -1) {
            return null;
        }

        String clientId = decoded.substring(0, pos);
        String secret = decoded.substring(pos + 1);

        String existingClientSecret = verticle.getClients().get(clientId);
        if (existingClientSecret == null) {
            log.info("Unknown clientId: " + clientId);
        }
        if (!secret.equals(existingClientSecret)) {
            return null;
        }
        return clientId;
    }

    private String authorizeClientUsingAssertion(final String clientId, final String clientAssertion, final String clientAssertionType) {
        if (clientId == null || clientAssertion == null || clientAssertionType == null) {
            return null;
        }
        if (!clientAssertion.equals(verticle.getClients().get(clientId))) {
            return null;
        }
        if (!"urn:ietf:params:oauth:client-assertion-type:jwt-bearer".equals(clientAssertionType)) {
            return null;
        }
        return clientId;
    }

    private String authorizeUser(String username, String password) {
        UserInfo userInfo = verticle.getUsers().get(username);
        if (userInfo == null || userInfo.password == null) {
            return null;
        }
        return userInfo.password.equals(password) ? username : null;
    }

    private void processJwksRequest(HttpServerRequest req, Mode mode) throws NoSuchAlgorithmException, JOSEException {
        if (req.method() != GET) {
            sendResponse(req, METHOD_NOT_ALLOWED);
            return;
        }
        switch (mode) {
            case MODE_200:
            case MODE_JWKS_RSA_WITH_SIG_USE:
                sendResponse(req, OK, jwksWithSig());
                break;
            case MODE_JWKS_RSA_WITHOUT_SIG_USE:
                sendResponse(req, OK, jwksWithoutSig());
                break;
            default:
                throw new IllegalStateException("Internal error");
        }
    }


    private String jwksWithSig() throws NoSuchAlgorithmException {
        return JSONUtil.asJson(new JWKSet(verticle.getSigKey()).toJSONObject()).toPrettyString();
    }

    private String jwksWithoutSig() throws NoSuchAlgorithmException, JOSEException {
        RSAKey jwk = verticle.getSigKey();
        jwk = new RSAKey.Builder(jwk.toRSAPublicKey())
                .privateKey(jwk.toRSAPrivateKey())
                .keyID(jwk.getKeyID())
                .build();
        return JSONUtil.asJson(new JWKSet(jwk).toJSONObject()).toPrettyString();
    }
}
