/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.services;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs;
import org.apache.kafka.common.security.auth.AuthenticationContext;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SaslAuthenticationContext;
import org.apache.kafka.common.security.authenticator.DefaultKafkaPrincipalBuilder;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.internals.OAuthBearerSaslServer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This class needs to be enabled as the PrincipalBuilder on Kafka Broker.
 * <p>
 * It ensures that additional session info is associated with the current session to allow enforcing access token lifetime,
 * for re-authentication to operate properly, and for custom authorizers to have access to additional session state -
 * i.e. the parsed access token. The extra information is in the form of <em>SessionInfo</em> object containing
 * the OAuthBearerToken token produced by <em>io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler</em>.
 * </p>
 * <p>
 * You can use 'principal.builder.class=io.strimzi.kafka.oauth.server.services.StrimziKafkaPrincipalBuilder'
 * property definition in server.properties to install it.
 * </p>
 */
public class StrimziKafkaPrincipalBuilder extends DefaultKafkaPrincipalBuilder implements Configurable {

    private static final SetAccessibleAction SET_PRINCIPAL_MAPPER = SetAccessibleAction.newInstance();

    private static class SetAccessibleAction implements PrivilegedAction<Void> {

        private Field field;

        SetAccessibleAction(Field field) {
            this.field = field;
        }

        @Override
        public Void run() {
            field.setAccessible(true);
            return null;
        }

        void invoke(DefaultKafkaPrincipalBuilder target, Object value) throws IllegalAccessException {
            AccessController.doPrivileged(this);
            field.set(target, value);
        }

        static SetAccessibleAction newInstance() {
            try {
                return new SetAccessibleAction(DefaultKafkaPrincipalBuilder.class.getDeclaredField("sslPrincipalMapper"));
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Failed to install StrimziKafkaPrincipalBuilder. This Kafka version does not seem to be supported", e);
            }
        }
    }


    public StrimziKafkaPrincipalBuilder() {
        super(null, null);
    }

    @Override
    public void configure(Map<String, ?> configs) {

        Object sslPrincipalMappingRules = configs.get(BrokerSecurityConfigs.SSL_PRINCIPAL_MAPPING_RULES_CONFIG);
        Object sslPrincipalMapper;

        try {
            Class<?> clazz = Class.forName("org.apache.kafka.common.security.ssl.SslPrincipalMapper");
            try {
                Method m = clazz.getMethod("fromRules", List.class);
                if (sslPrincipalMappingRules == null) {
                    sslPrincipalMappingRules = Collections.singletonList("DEFAULT");
                }
                sslPrincipalMapper = m.invoke(null, sslPrincipalMappingRules);

            } catch (NoSuchMethodException ex) {
                Method m = clazz.getMethod("fromRules", String.class);
                if (sslPrincipalMappingRules == null) {
                    sslPrincipalMappingRules = "DEFAULT";
                }
                sslPrincipalMapper = m.invoke(null, sslPrincipalMappingRules);
            }

            // Hack setting sslPrincipalMapper to DefaultKafkaPrincipalBuilder
            // An alternative would be to copy paste the complete DefaultKafkaPrincipalBuilder implementation
            // into this class and extend it

            SET_PRINCIPAL_MAPPER.invoke(this, sslPrincipalMapper);

        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to initialize StrimziKafkaPrincipalBuilder", e);

        } catch (ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException e) {
            throw new RuntimeException("Failed to initialize StrimziKafkaPrincipalBuilder", e);
        }
    }

    @Override
    public KafkaPrincipal build(AuthenticationContext context) {
        if (context instanceof SaslAuthenticationContext) {
            OAuthBearerSaslServer server = (OAuthBearerSaslServer) ((SaslAuthenticationContext) context).server();
            if (OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(server.getMechanismName())) {
                KafkaPrincipal kafkaPrincipal = new KafkaPrincipal(KafkaPrincipal.USER_TYPE,
                        server.getAuthorizationID());

                BearerTokenWithPayload token = (BearerTokenWithPayload) server.getNegotiatedProperty("OAUTHBEARER.token");
                SessionInfo info = new SessionInfo();
                info.setToken(token);
                Services.getInstance().getSessions().put(kafkaPrincipal, info);

                return kafkaPrincipal;
            }
        }

        return super.build(context);
    }
}
