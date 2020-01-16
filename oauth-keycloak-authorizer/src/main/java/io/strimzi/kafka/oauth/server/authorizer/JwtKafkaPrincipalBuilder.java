/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import org.apache.kafka.common.security.auth.AuthenticationContext;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SaslAuthenticationContext;
import org.apache.kafka.common.security.authenticator.DefaultKafkaPrincipalBuilder;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.internals.OAuthBearerSaslServer;

/**
 * This class needs to be enabled as the PrincipalBuilder on Kafka Broker.
 * <p>
 * It ensures that the generated Principal is instance of {@link io.strimzi.kafka.oauth.server.authorizer.JwtKafkaPrincipal},
 * containing the OAuthBearerToken token produced by <em>io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler</em>.
 * </p>
 * <p>
 * You can use 'principal.builder.class=io.strimzi.kafka.oauth.server.authorizer.JwtKafkaPrincipalBuilder'
 * property definition in server.properties to install it.
 * </p>
 */
public class JwtKafkaPrincipalBuilder extends DefaultKafkaPrincipalBuilder {

    public JwtKafkaPrincipalBuilder() {
        super(null, null);
    }

    @Override
    public KafkaPrincipal build(AuthenticationContext context) {
        if (context instanceof SaslAuthenticationContext) {
            OAuthBearerSaslServer server = (OAuthBearerSaslServer) ((SaslAuthenticationContext) context).server();
            if (OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(server.getMechanismName())) {
                return new JwtKafkaPrincipal(KafkaPrincipal.USER_TYPE,
                        server.getAuthorizationID(),
                        (BearerTokenWithPayload) server.getNegotiatedProperty("OAUTHBEARER.token"));
            }
        }

        return super.build(context);
    }
}
