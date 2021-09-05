/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.strimzi.testsuite.oauth.server.Endpoint.INTROSPECT;
import static io.strimzi.testsuite.oauth.server.Endpoint.JWKS;
import static io.strimzi.testsuite.oauth.server.Endpoint.SERVER;
import static io.strimzi.testsuite.oauth.server.Endpoint.TOKEN;
import static io.strimzi.testsuite.oauth.server.Endpoint.USERINFO;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_400;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_401;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_404;

/**
 * A simple remote controllable authorization server for testing various error situations
 * <p>
 * This Verticle sets up two servers on two ports:
 * <ul>
 *     <li><tt>8090</tt> A testing authorization server using https</li>
 *     <li><tt>8091</tt> An admin server (remote control server) that is used to control the testing authorization server</li>
 * </ul>
 *
 * The authorization server provides the following endpoints:
 * <ul>
 *     <li>/jwks</li>
 *     <li>/introspect</li>
 *     <li>/userinfo</li>
 *     <li>/token</li>
 * </ul>
 *
 * The admin server allows configuring for each of these endpoints what an error status it should return.
 *
 * Use PUT or POST to any of these endpoints with <tt>?mode=MODE</tt> where MODE is one of:
 * <ul>
 *     <li>MODE_400</li>
 *     <li>MODE_401</li>
 *     <li>MODE_403</li>
 *     <li>MODE_404</li>
 *     <li>MODE_500</li>
 *     <li>MODE_503</li>
 * </ul>
 *
 *  For example, <tt>POST /jwks?mode=MODE_404</tt> will configure the authorization server to always return status 404
 *  when any request is performed against <tt>/jwks</tt> endpoint.
 *
 *  Additionally, the admin server exposes another endpoint:
 *  <ul>
 *      <li>/server</li>
 *  </ul>
 *
 *  Which is used to control the state of the authorization server itself rather than its specific endpoints.
 *  The following modes are available:
 *  <ul>
 *      <li><tt>MODE_OFF</tt> to unbind the server port 8090</li>
 *      <li><tt>MODE_CERT_ONE_ON</tt> to bind the server using <tt>https</tt> with a valid certificate</li>
 *      <li><tt>MODE_CERT_TWO_ON</tt> to bind the server using <tt>https</tt> with a different certificate</li>
 *      <li><tt>MODE_EXPIRED_CERT_ON</tt> to bind the server using <tt>https</tt> with expired certificate</li>
 *  </ul>
 *
 *  The server is bound on all the network interfaces, but certificates are created for the name <tt>mockoauth</tt>.
 *  Rather than accessing this server using the local hostname or a loopback IP you should add <tt>mockoauth</tt> to your
 *  <tt>/etc/host</tt> and in <tt>.travis.yml</tt>.
 */
public class MockOAuthServerMainVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(MockOAuthServerMainVerticle.class);

    Future<HttpServer> authServer;

    Map<Endpoint, Mode> modes = new HashMap<>();

    String keystoreOnePath;
    String keystoreOnePass;
    String keystoreTwoPath;
    String keystoreTwoPass;
    String keystoreExpiredPath;
    String keystoreExpiredPass;

    public void start() {

        modes.put(JWKS, MODE_404);
        modes.put(TOKEN, MODE_400);
        modes.put(INTROSPECT, MODE_401);
        modes.put(USERINFO, MODE_401);

        keystoreOnePath = getEnvVar("KEYSTORE_ONE_PATH", "../docker/certificates/mockoauth.server.keystore.p12");
        keystoreOnePass = getEnvVar("KEYSTORE_ONE_PASSWORD", "changeit");

        keystoreTwoPath = getEnvVar("KEYSTORE_TWO_PATH", "../docker/certificates/mockoauth.server.keystore_2.p12");
        keystoreTwoPass = getEnvVar("KEYSTORE_TWO_PASSWORD", "changeit");

        keystoreExpiredPath = getEnvVar("KEYSTORE_EXPIRED_PATH", "../docker/certificates/mockoauth.server.keystore_expired.p12");
        keystoreExpiredPass = getEnvVar("KEYSTORE_EXPIRED_PASSWORD", "changeit");

        // Start admin server
        vertx.createHttpServer().requestHandler(new AdminServerRequestHandler(this)).listen(8091);

        ensureAuthServerWithFirstCert();
    }

    void ensureAuthServer(String keystorePath, String keystorePass) {
        if (authServer == null) {
            JksOptions keyOptions = new JksOptions();
            keyOptions.setPath(keystorePath);
            keyOptions.setPassword(keystorePass);

            authServer = vertx.createHttpServer(new HttpServerOptions()
                    .setSsl(true)
                    .setKeyStoreOptions(keyOptions)
                ).requestHandler(new AuthServerRequestHandler(this)).listen(8090);

            if (authServer.failed()) {
                log.error("Failed to start Mock OAuth Server: ", authServer.cause());
                authServer = null;
            }
        }
    }

    void ensureAuthServerWithFirstCert() {
        if (authServer != null) {
            shutdownAuthServer();
        }
        ensureAuthServer(keystoreOnePath, keystoreOnePass);
        setServerMode(Mode.MODE_CERT_ONE_ON);
    }

    void ensureAuthServerWithSecondCert() {
        if (authServer != null) {
            shutdownAuthServer();
        }
        ensureAuthServer(keystoreTwoPath, keystoreTwoPass);
        setServerMode(Mode.MODE_CERT_TWO_ON);
    }

    void ensureAuthServerWithExpiredCert() {
        if (authServer != null) {
            shutdownAuthServer();
        }
        ensureAuthServer(keystoreExpiredPath, keystoreExpiredPass);
        setServerMode(Mode.MODE_EXPIRED_CERT_ON);
    }

    void shutdownAuthServer() {
        if (authServer != null) {
            authServer.result().close();
            authServer = null;
        }
        setServerMode(null);
    }

    private void setServerMode(Mode mode) {
        if (authServer != null) {
            setMode(SERVER, mode);
        }
    }

    void setMode(Endpoint e, Mode m) {
        modes.put(e, m);
    }

    Mode getMode(Endpoint e) {
        return modes.get(e);
    }

    private static String getEnvVar(String name, String defaultValue) {
        String val = System.getenv(name);
        return val != null ? val : defaultValue;
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MockOAuthServerMainVerticle());
    }
}