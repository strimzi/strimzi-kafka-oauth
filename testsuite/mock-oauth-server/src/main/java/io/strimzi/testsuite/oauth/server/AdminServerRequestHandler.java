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

public class AdminServerRequestHandler implements Handler<HttpServerRequest> {

    private static final Logger log = LoggerFactory.getLogger(AdminServerRequestHandler.class);

    private final MockOAuthServerMainVerticle verticle;

    public AdminServerRequestHandler(MockOAuthServerMainVerticle verticle) {
        this.verticle = verticle;
    }

    @Override
    public void handle(HttpServerRequest req) {

        if (req.method() != HttpMethod.POST &&
                req.method() != HttpMethod.PUT &&
                req.method() != HttpMethod.GET) {
            req.response().setStatusCode(405)
                    .setStatusMessage("Method not allowed")
                    .end();
            return;
        }
        String[] path = req.path().split("/");
        if (path.length < 3 || !"admin".equals(path[1])) {
            req.response().setStatusCode(404)
                    .setStatusMessage("Not Found")
                    .end();
            return;
        }

        try {
            Endpoint endpoint = Endpoint.fromString(path[2]);

            if (req.method() == HttpMethod.GET) {
                req.response()
                        .putHeader("Content-Type", "text/plain")
                        .end(String.valueOf(verticle.getMode(endpoint)));
                return;
            }

            String mode = req.params().get("mode");
            if (mode == null) {
                req.response().setStatusCode(400)
                        .setStatusMessage("Bad request")
                        .end("Parameter 'mode' not set");
                return;
            }

            Mode m = Mode.fromString(mode);

            if (endpoint == Endpoint.SERVER) {
                switch (m) {
                    case MODE_CERT_ONE_ON:
                        verticle.ensureAuthServerWithFirstCert();
                        break;
                    case MODE_CERT_TWO_ON:
                        verticle.ensureAuthServerWithSecondCert();
                        break;
                    case MODE_EXPIRED_CERT_ON:
                        verticle.ensureAuthServerWithExpiredCert();
                        break;
                    case MODE_OFF:
                        verticle.shutdownAuthServer();
                        break;
                    default:
                }
            } else {
                verticle.setMode(endpoint, m);
            }

            req.response()
                .putHeader("Content-Type", "text/plain")
                .end(String.valueOf(m));

        } catch (Exception e) {
            log.error("Failed to set new mode: ", e);

            req.response().setStatusCode(400)
                    .setStatusMessage("Bad request")
                    .end();
        }
    }

}
