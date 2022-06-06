/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

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
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.util.Date;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static io.strimzi.kafka.oauth.common.OAuthAuthenticator.base64decode;
import static io.strimzi.testsuite.oauth.server.Commons.handleFailure;
import static io.strimzi.testsuite.oauth.server.Commons.isOneOf;
import static io.strimzi.testsuite.oauth.server.Commons.sendResponse;
import static io.strimzi.testsuite.oauth.server.Commons.setContextLog;
import static io.strimzi.testsuite.oauth.server.Endpoint.TOKEN;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_200;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_JWKS_RSA_WITHOUT_SIG_USE;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_JWKS_RSA_WITH_SIG_USE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;

public class AuthServerRequestHandler implements Handler<HttpServerRequest> {

    private static final Logger log = LoggerFactory.getLogger("oauth");

    private final MockOAuthServerMainVerticle verticle;

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
            if (endpoint == Endpoint.JWKS &&
                    isOneOf(mode, MODE_200, MODE_JWKS_RSA_WITH_SIG_USE, MODE_JWKS_RSA_WITHOUT_SIG_USE)) {
                processJwksRequest(req, mode);
                return;

            } else if (endpoint == TOKEN && mode == MODE_200) {
                processTokenRequest(req);
                return;
            }

            switch (mode) {
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
                    log.error("Unexpected mode: " + mode);
                    sendResponse(req, OK, "" + verticle.getMode(endpoint));
            }

        } catch (Throwable t) {
            handleFailure(req, t, log);
        }
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

            String grantType = form.get("grant_type");
            if (grantType == null) {
                sendResponse(req, BAD_REQUEST);
                return;
            }

            String authorization = req.headers().get("Authorization");
            String clientId = authorize(authorization);

            if (!grantType.equals("client_credentials") || clientId == null) {
                sendResponse(req, UNAUTHORIZED);
                return;
            }

            try {
                // Create a signed JWT token
                String accessToken = createSignedAccessToken(clientId);

                JsonObject result = new JsonObject();
                result.put("access_token", accessToken);
                result.put("expires_in", 60_000);
                result.put("scope", "all");

                String jsonString = result.encode();
                sendResponse(req, OK, jsonString);

            } catch (Throwable t) {
                handleFailure(req, t, log);
            }
        });
    }

    private String createSignedAccessToken(String clientId) throws JOSEException, NoSuchAlgorithmException {

        // Create RSA-signer with the private key
        JWSSigner signer = new RSASSASigner(verticle.getSigKey());

        // Prepare JWT with claims set
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(clientId)
                .issuer("https://mockoauth:8090")
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(verticle.getSigKey().getKeyID()).build(),
                claimsSet);

        // Compute the RSA signature
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    private String authorize(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return null;
        }
        String decoded = base64decode(authorization.substring(6));
        String[] idSecret = decoded.split(":");
        if (idSecret.length != 2) {
            return null;
        }

        if (!idSecret[1].equals(verticle.getClients().get(idSecret[0]))) {
            return null;
        }
        return idSecret[0];
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
