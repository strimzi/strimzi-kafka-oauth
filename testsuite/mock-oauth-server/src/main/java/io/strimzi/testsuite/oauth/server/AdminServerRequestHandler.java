/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.strimzi.testsuite.oauth.server.Commons.handleFailure;
import static io.strimzi.testsuite.oauth.server.Commons.isOneOf;
import static io.strimzi.testsuite.oauth.server.Commons.sendResponse;
import static io.strimzi.testsuite.oauth.server.Commons.setContextLog;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;

public class AdminServerRequestHandler implements Handler<HttpServerRequest> {

    private static final Logger log = LoggerFactory.getLogger("admin");

    private final MockOAuthServerMainVerticle verticle;

    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public AdminServerRequestHandler(MockOAuthServerMainVerticle verticle) {
        this.verticle = verticle;
    }

    @Override
    public void handle(HttpServerRequest req) {
        log.info("> " + req.method().name() + " " + req.path());
        setContextLog(log);

        if (!isOneOf(req.method(), POST, PUT, GET)) {
            sendResponse(req, METHOD_NOT_ALLOWED);
            return;
        }
        String[] path = req.path().split("/");
        if (path.length < 3 || !"admin".equals(path[1])) {
            sendResponse(req, NOT_FOUND);
            return;
        }

        try {
            Endpoint endpoint = Endpoint.fromString(path[2]);

            if (endpoint == Endpoint.CLIENTS) {
                processClientsRequest(req, path);
                return;
            } else if (endpoint == Endpoint.USERS) {
                processUsersRequest(req, path);
                return;
            } else if (endpoint == Endpoint.REVOCATIONS) {
                processRevocation(req, path);
                return;
            }

            if (req.method() == GET) {
                sendResponse(req, OK, "" + verticle.getMode(endpoint));
                return;
            }

            String mode = req.params().get("mode");
            if (mode == null) {
                sendResponse(req, BAD_REQUEST, "Parameter 'mode' not set");
                return;
            }

            Mode m = Mode.fromString(mode);

            Future<Void> result = null;
            if (endpoint == Endpoint.SERVER) {
                switch (m) {
                    case MODE_CERT_ONE_ON:
                        result = verticle.ensureAuthServerWithFirstCert();
                        break;
                    case MODE_CERT_TWO_ON:
                        result = verticle.ensureAuthServerWithSecondCert();
                        break;
                    case MODE_EXPIRED_CERT_ON:
                        result = verticle.ensureAuthServerWithExpiredCert();
                        break;
                    case MODE_OFF:
                        result = verticle.shutdownAuthServer();
                        break;
                    default:
                }
            } else {
                verticle.setMode(endpoint, m);
            }

            if (result != null) {
                result
                    .onSuccess(v -> sendResponse(req, OK, m.name()))
                    .onFailure(e -> handleFailure(req, e, log));
            } else {
                sendResponse(req, OK, m.name());
            }
        } catch (Exception e) {
            handleFailure(req, e, log);
        }
    }

    private void processRevocation(HttpServerRequest req, String[] path) {
        if (path.length > 3) {
            sendResponse(req, NOT_FOUND);
            return;
        }

        if (req.method() == GET) {
            sendResponse(req, OK, getRevokedTokensAsJsonString());
            return;

        } else if (isOneOf(req.method(), POST, PUT)) {

            req.bodyHandler(buffer -> {
                try {
                    log.info(buffer.toString());

                    JsonObject json = buffer.toJsonObject();

                    String token = json.getString("token");
                    if (token == null) {
                        sendResponse(req, BAD_REQUEST, "Required attribute 'token' is null or missing.");
                        return;
                    }

                    verticle.revokeToken(token);
                    sendResponse(req, OK);

                } catch (Exception e) {
                    handleFailure(req, e, log);
                }
            });
            return;
        }

        sendResponse(req, METHOD_NOT_ALLOWED);
    }

    private void processUsersRequest(HttpServerRequest req, String[] path) {
        if (path.length > 3) {
            sendResponse(req, NOT_FOUND);
            return;
        }

        if (req.method() == GET) {
            sendResponse(req, OK, getUsersAsJsonString());
            return;

        } else if (isOneOf(req.method(), POST, PUT)) {

            req.bodyHandler(buffer -> {
                try {
                    log.info(buffer.toString());

                    JsonObject json = buffer.toJsonObject();

                    String username = json.getString("username");
                    if (username == null) {
                        sendResponse(req, BAD_REQUEST, "Required attribute 'username' is null or missing.");
                        return;
                    }

                    String password = json.getString("password");
                    if (password == null) {
                        sendResponse(req, BAD_REQUEST, "Required attribute 'password' is null or missing.");
                        return;
                    }

                    verticle.createOrUpdateUser(username, password);
                    sendResponse(req, OK);

                } catch (Exception e) {
                    handleFailure(req, e, log);
                }
            });
            return;
        }

        sendResponse(req, METHOD_NOT_ALLOWED);
    }

    private void processClientsRequest(HttpServerRequest req, String[] path) {

        if (path.length > 3) {
            sendResponse(req, NOT_FOUND);
            return;
        }

        if (req.method() == GET) {
            sendResponse(req, OK, getClientsAsJsonString());
            return;

        } else if (isOneOf(req.method(), POST, PUT)) {

            req.bodyHandler(buffer -> {
                try {
                    log.info(buffer.toString());

                    JsonObject json = buffer.toJsonObject();

                    String clientId = json.getString("clientId");
                    if (clientId == null) {
                        sendResponse(req, BAD_REQUEST, "Required attribute 'clientId' is null or missing.");
                        return;
                    }

                    String secret = json.getString("secret");
                    if (secret == null) {
                        sendResponse(req, BAD_REQUEST, "Required attribute 'secret' is null or missing.");
                        return;
                    }

                    verticle.createOrUpdateClient(clientId, secret);
                    sendResponse(req, OK);

                } catch (Exception e) {
                    handleFailure(req, e, log);
                }
            });
            return;
        }

        sendResponse(req, METHOD_NOT_ALLOWED);
    }

    private String getClientsAsJsonString() {
        JsonArray result = new JsonArray();
        for (Map.Entry<String, String> ent: verticle.getClients().entrySet()) {
            JsonObject json = new JsonObject();
            json.put(ent.getKey(), ent.getValue());
            result.add(json);
        }
        return result.toString();
    }

    private String getUsersAsJsonString() {
        JsonArray result = new JsonArray();
        for (Map.Entry<String, String> ent: verticle.getUsers().entrySet()) {
            JsonObject json = new JsonObject();
            json.put(ent.getKey(), ent.getValue());
            result.add(json);
        }
        return result.toString();
    }

    private String getRevokedTokensAsJsonString() {
        JsonArray result = new JsonArray();
        for (String token: verticle.getRevokedTokens()) {
            result.add(token);
        }
        return result.toString();
    }
}
