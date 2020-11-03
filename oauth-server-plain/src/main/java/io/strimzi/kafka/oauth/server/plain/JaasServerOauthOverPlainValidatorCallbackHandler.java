/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.plain;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler;
import io.strimzi.kafka.oauth.server.OAuthKafkaPrincipal;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallback;
import org.apache.kafka.common.security.plain.PlainAuthenticateCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.util.List;
import java.util.Map;

public class JaasServerOauthOverPlainValidatorCallbackHandler extends JaasServerOauthValidatorCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(JaasServerOauthOverPlainValidatorCallbackHandler.class);

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {

        if (!"PLAIN".equals(saslMechanism))    {
            throw new IllegalArgumentException(String.format("Unexpected SASL mechanism: %s", saslMechanism));
        }

        if (jaasConfigEntries.size() != 1) {
            throw new IllegalArgumentException("Exactly one jaasConfigEntry expected (size: " + jaasConfigEntries.size());
        }

        super.configure(configs, "OAUTHBEARER", jaasConfigEntries);
    }



    @Override
    public void close() {
        super.close();
    }

    @Override
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
        String username = null;
        String password = null;
        org.apache.kafka.common.security.plain.PlainAuthenticateCallback cb = null;

        for (Callback callback : callbacks) {
            if (callback instanceof javax.security.auth.callback.NameCallback) {
                username = ((javax.security.auth.callback.NameCallback) callback).getDefaultName();
            } else if (callback instanceof org.apache.kafka.common.security.plain.PlainAuthenticateCallback) {
                password = String.valueOf(((org.apache.kafka.common.security.plain.PlainAuthenticateCallback) callback).password());
                cb = (org.apache.kafka.common.security.plain.PlainAuthenticateCallback) callback;
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }

        handleCallback(cb, username, password);
    }

    private void handleCallback(PlainAuthenticateCallback callback, String username, String password) {
        if (callback == null) throw new IllegalArgumentException("callback == null");
        if (username == null) throw new IllegalArgumentException("username == null");

        try {
            authenticate(username, password);
            callback.authenticated(true);
        } catch (Exception e) {
            throw new SaslAuthenticationException("Authentication failed: " + getCauseMessage(e), e);
        }
    }

    private void authenticate(String username, String apiKey) throws UnsupportedCallbackException {
        System.out.println("authenticate(): " + username + ":" + apiKey);
        // we assume the api key is the access token
        // username is the client id
        OAuthBearerValidatorCallback[] callbacks = new OAuthBearerValidatorCallback[] {new OAuthBearerValidatorCallback(apiKey)};
        super.handle(callbacks);

        OAuthBearerToken token = callbacks[0].token();
        if (token == null) {
            throw new RuntimeException("Authentication with OAuth token failed");
        }

        OAuthKafkaPrincipal kafkaPrincipal = new OAuthKafkaPrincipal(KafkaPrincipal.USER_TYPE,
                username, (BearerTokenWithPayload) token);
        OAuthKafkaPrincipal.pushCurrentPrincipal(kafkaPrincipal);
    }

    private static String getCauseMessage(Throwable e) {
        StringBuilder sb = new StringBuilder(e.toString());

        Throwable t = e;
        while ((t = t.getCause()) != null) {
            sb.append(", caused by: ").append(t.toString());
        }
        return sb.toString();
    }
}
