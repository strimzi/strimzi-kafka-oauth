/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthServerRequestHandler implements Handler<HttpServerRequest> {

    private static final Logger log = LoggerFactory.getLogger(AuthServerRequestHandler.class);

    private final MockOAuthServerMainVerticle verticle;

    public AuthServerRequestHandler(MockOAuthServerMainVerticle verticle) {
        this.verticle = verticle;
    }

    @Override
    public void handle(HttpServerRequest req) {
        if (req.method() != HttpMethod.GET) {
            req.response().setStatusCode(405)
                    .setStatusMessage("Method not allowed")
                    .end();
            return;
        }

        String[] path = req.path().split("/");

        if (path.length != 2) {
            req.response().setStatusCode(404)
                    .setStatusMessage("Not Found")
                    .end();
            return;
        }

        Endpoint endpoint = Endpoint.fromString(path[1]);
        Mode mode = verticle.getMode(endpoint);

        switch (mode) {
            case MODE_400:
                req.response().setStatusCode(400)
                        .setStatusMessage("Bad Request")
                        .end();
                break;
            case MODE_401:
                req.response().setStatusCode(401)
                        .setStatusMessage("Unauthorized")
                        .end();
                break;
            case MODE_403:
                req.response().setStatusCode(403)
                        .setStatusMessage("Forbidden")
                        .end();
                break;
            case MODE_404:
                req.response().setStatusCode(404)
                        .setStatusMessage("Not Found")
                        .end();
                break;
            case MODE_500:
                req.response().setStatusCode(500)
                        .setStatusMessage("Internal Server Error")
                        .end();
                break;
            case MODE_503:
                req.response().setStatusCode(503)
                        .setStatusMessage("Service Unavailable")
                        .end();
                break;
            default:
                log.error("Unexpected mode: " + mode);
                req.response()
                        .putHeader("Content-Type", "text/plain")
                        .end("" + verticle.getMode(endpoint));

        }
    }
}
