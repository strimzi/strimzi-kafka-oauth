/*
 * Copyright 2017-2021, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.strimzi.testsuite.oauth.server.Endpoint.INTROSPECT;
import static io.strimzi.testsuite.oauth.server.Endpoint.JWKS;
import static io.strimzi.testsuite.oauth.server.Endpoint.SERVER;
import static io.strimzi.testsuite.oauth.server.Endpoint.TOKEN;
import static io.strimzi.testsuite.oauth.server.Endpoint.USERINFO;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_400;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_401;
import static io.strimzi.testsuite.oauth.server.Mode.MODE_404;

/**
 * A simple remote controllable authorization server for testing various error situations.
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
 * The admin server allows configuring for each of these endpoints what it should return.
 *
 * Use PUT or POST to any of these endpoints prepended by '/admin' (e.g. '/admin/jwks') with <tt>?mode=MODE</tt> where MODE is one of:
 * <ul>
 *     <li>MODE_200</li>
 *     <li>MODE_400</li>
 *     <li>MODE_401</li>
 *     <li>MODE_403</li>
 *     <li>MODE_404</li>
 *     <li>MODE_500</li>
 *     <li>MODE_503</li>
 * </ul>
 *
 * Some other modes are endpoint specific.
 *
 * For 'jwks' there are two specific modes:
 * <ul>
 *     <li>MODE_JWKS_RSA_WITH_SIG_USE</li>
 *     <li>MODE_JWKS_RSA_WITHOUT_SIG_USE</li>
 * </ul>
 *
 * For example, <tt>POST /admin/jwks?mode=MODE_404</tt> will configure the authorization server to always return status 404
 * when any request is performed against <tt>/jwks</tt> endpoint.
 *
 * The <tt>MODE_200</tt> mode results in the endpoint behaving as a properly functioning OAuth server. For example,
 * <tt>POST /admin/jwks?mode=MODE_200</tt> will instruct the authorization server to return public keys for signature validation.
 * Specifically, the behaviour will be the same as if setting it to MODE_JWKS_RSA_WITH_SIG_USE.
 *
 * And setting <tt>/admin/token</tt> to <tt>MODE_200</tt> instructs the authorization server that the token endpoint should issue new tokens,
 * using the <tt>client_credentials</tt> grant type.
 *
 * <br><br>
 * The admin server exposes two additional endpoints:
 * <ul>
 *     <li>/admin/server</li>
 *     <li>/admin/clients</li>
 * </ul>
 *
 * <tt>/admin/server</tt> is used to control the state of the authorization server itself rather than its specific endpoints.
 * The following modes are available:
 * <ul>
 *     <li><tt>MODE_OFF</tt> to unbind the server port 8090</li>
 *     <li><tt>MODE_CERT_ONE_ON</tt> to bind the server using <tt>https</tt> with a valid certificate</li>
 *     <li><tt>MODE_CERT_TWO_ON</tt> to bind the server using <tt>https</tt> with a different certificate</li>
 *     <li><tt>MODE_EXPIRED_CERT_ON</tt> to bind the server using <tt>https</tt> with expired certificate</li>
 * </ul>
 *
 * <tt>/admin/clients</tt> is used to add client definitions. A client has a <tt>clientId</tt> and a <tt>secret</tt>.
 *
 * They can be added by posting a JSON document to <tt>/admin/clients</tt> containing the attributes, e.g.:
 * <pre>
 * {
 *     "clientId": "testclient",
 *     "secret": "testsecret"
 * }
 * </pre>
 *
 * The server is bound on all the network interfaces, but certificates are created for the name <tt>mockoauth</tt>.
 * Rather than accessing this server using the local hostname or a loopback IP you should add <tt>mockoauth</tt> to your
 * <tt>/etc/host</tt> and in <tt>.travis.yml</tt>.
 */
public class MockOAuthServerMainVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(MockOAuthServerMainVerticle.class);

    private Future<HttpServer> authServer;

    private final Map<Endpoint, Mode> modes = new HashMap<>();

    private String keystoreOnePath;
    private String keystoreOnePass;
    private String keystoreTwoPath;
    private String keystoreTwoPass;
    private String keystoreExpiredPath;
    private String keystoreExpiredPass;

    private final Map<String, String> clients = new HashMap<>();
    private RSAKey sigKey;

    public void start() {

        modes.put(JWKS, MODE_404);
        modes.put(TOKEN, MODE_400);
        modes.put(INTROSPECT, MODE_401);
        modes.put(USERINFO, MODE_401);

        String projectRoot = getProjectRoot();
        keystoreOnePath = getEnvVar("KEYSTORE_ONE_PATH", projectRoot + "/../docker/certificates/mockoauth.server.keystore.p12");
        keystoreOnePass = getEnvVar("KEYSTORE_ONE_PASSWORD", "changeit");

        keystoreTwoPath = getEnvVar("KEYSTORE_TWO_PATH", projectRoot + "/../docker/certificates/mockoauth.server.keystore_2.p12");
        keystoreTwoPass = getEnvVar("KEYSTORE_TWO_PASSWORD", "changeit");

        keystoreExpiredPath = getEnvVar("KEYSTORE_EXPIRED_PATH", projectRoot + "/../docker/certificates/mockoauth.server.keystore_expired.p12");
        keystoreExpiredPass = getEnvVar("KEYSTORE_EXPIRED_PASSWORD", "changeit");

        // Start admin server
        vertx.createHttpServer().requestHandler(new AdminServerRequestHandler(this)).listen(8091);

        ensureAuthServerWithFirstCert();
    }

    private static String getProjectRoot() {
        String cwd = System.getProperty("user.dir");
        Path path = Paths.get(cwd);
        if (path.endsWith("mock-oauth-server") && Files.exists(path.resolve("../docker"))) {
            return path.toAbsolutePath().toString();
        } else if (path.endsWith("strimzi-kafka-oauth") && Files.exists(path.resolve("testsuite/docker")) && Files.exists(path.resolve("testsuite/mock-oauth-server")))  {
            return path.resolve("testsuite/mock-oauth-server").toAbsolutePath().toString();
        }
        return cwd;
    }

    Future<Void> ensureAuthServer(String keystorePath, String keystorePass, Mode mode) {
        Promise<Void> result = Promise.promise();

        if (authServer == null) {
            JksOptions keyOptions = new JksOptions();
            keyOptions.setPath(keystorePath);
            keyOptions.setPassword(keystorePass);

            authServer = vertx.createHttpServer(new HttpServerOptions()
                    .setSsl(true)
                    .setKeyStoreOptions(keyOptions)
                )
                    .requestHandler(new AuthServerRequestHandler(this))
                    .listen(8090)
                    .onSuccess(server -> {
                        log.info("ensureAuthServer(): AuthServer started successfully");
                        setServerMode(mode);
                        result.complete();
                    })
                    .onFailure(t -> {
                        log.error("ensureAuthServer(): Failed to start Mock OAuth Server: ", t);
                        authServer = null;
                        result.fail(t);
                    });
        } else {
            result.complete();
        }

        return result.future();
    }

    Future<Void> ensureAuthServerWithFirstCert() {
        return shutdownAuthServer()
            .onSuccess(r -> ensureAuthServer(keystoreOnePath, keystoreOnePass, Mode.MODE_CERT_ONE_ON));
    }

    Future<Void> ensureAuthServerWithSecondCert() {
        return shutdownAuthServer()
            .onSuccess(r -> ensureAuthServer(keystoreTwoPath, keystoreTwoPass, Mode.MODE_CERT_TWO_ON));
    }

    Future<Void>  ensureAuthServerWithExpiredCert() {
        return shutdownAuthServer()
            .onSuccess(r -> ensureAuthServer(keystoreExpiredPath, keystoreExpiredPass, Mode.MODE_EXPIRED_CERT_ON));
    }

    Future<Void> shutdownAuthServer() {
        Promise<Void> result = Promise.promise();

        if (authServer != null) {
            authServer.result().close()
                    .onSuccess(v -> {
                        log.error("shutdownAuthServer(): AuthServer shut down successfully");
                        authServer = null;
                        setServerMode(null);
                        result.complete();
                    })
                    .onFailure(t -> {
                        log.error("shutdownAuthServer(): Failed to shut down Mock OAuth Server: ", t);
                        result.fail(t);
                    });
        } else {
            result.complete();
        }

        return result.future();
    }

    private void setServerMode(Mode mode) {
        setMode(SERVER, mode);
    }

    void setMode(Endpoint e, Mode m) {
        modes.put(e, m);
    }

    Mode getMode(Endpoint e) {
        return modes.get(e);
    }

    synchronized RSAKey getSigKey() throws NoSuchAlgorithmException {
        if (sigKey != null) {
            return sigKey;
        }

        // Generate the RSA key pair
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();

        // Convert to JWK format
        sigKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .keyUse(KeyUse.SIGNATURE)
                .build();
        return sigKey;
    }

    void createOrUpdateClient(String clientId, String secret) {
        clients.put(clientId, secret);
    }

    Map<String, String> getClients() {
        return Collections.unmodifiableMap(clients);
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