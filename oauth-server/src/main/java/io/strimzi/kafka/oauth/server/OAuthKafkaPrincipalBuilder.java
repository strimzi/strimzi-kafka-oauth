/*
 * Copyright 2017-2020, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import io.strimzi.kafka.oauth.common.BearerTokenWithPayload;
import io.strimzi.kafka.oauth.services.Principals;
import io.strimzi.kafka.oauth.services.Services;
import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.message.DefaultPrincipalData;
import org.apache.kafka.common.protocol.ByteBufferAccessor;
import org.apache.kafka.common.protocol.MessageUtil;
import org.apache.kafka.common.protocol.types.RawTaggedField;
import org.apache.kafka.common.security.auth.AuthenticationContext;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.auth.SaslAuthenticationContext;
import org.apache.kafka.common.security.authenticator.DefaultKafkaPrincipalBuilder;
import org.apache.kafka.common.security.kerberos.KerberosShortNamer;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule;
import org.apache.kafka.common.security.oauthbearer.internals.OAuthBearerSaslServer;
import org.apache.kafka.common.security.plain.internals.PlainSaslServer;

import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.sasl.SaslServer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
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
 * <p>
 * It is also required for OAuth over PLAIN to operate properly.
 * <p>
 * As a result, a listener configuration that uses SASL PLAIN mechanism without expecting it to work as OAuth over PLAIN
 * <em>will not</em> work.
 * <p>
 * For example, the following configuration will not work as it will assume the usage of OAuth over PLAIN, rather than simple PLAIN:
 * <pre>
 *  listener.name.controller.sasl.enabled.mechanism=PLAIN
 *  listener.name.controller.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required    username="admin"    password="admin-password"    user_admin="admin-password"    user_bobby="bobby-secret" ;
 * </pre>
 * In such a case the following exception may be thrown and output in the log:
 * <pre>
 *     java.lang.IllegalStateException: Services object has not been properly initialised
 * </pre>
 * <p>
 * Use 'principal.builder.class=io.strimzi.kafka.oauth.server.OAuthKafkaPrincipalBuilder'
 * property definition in server.properties to install this custom PrincipalBuilder.
 */
public class OAuthKafkaPrincipalBuilder extends DefaultKafkaPrincipalBuilder implements Configurable {

    private static final SetAccessibleAction SET_PRINCIPAL_MAPPER = SetAccessibleAction.newInstance("sslPrincipalMapper");
    private static final SetAccessibleAction SET_KERBEROS_SHORT_NAMER = SetAccessibleAction.newInstance("kerberosShortNamer");

    private static final int OAUTH_DATA_TAG = 575;

    private static class SetAccessibleAction implements PrivilegedAction<Void> {

        private final Field field;

        SetAccessibleAction(Field field) {
            this.field = field;
        }

        @Override
        public Void run() {
            field.setAccessible(true);
            return null;
        }

        void invoke(DefaultKafkaPrincipalBuilder target, Object value) throws IllegalAccessException {
            java.security.AccessController.doPrivileged(this);
            field.set(target, value);
        }

        static SetAccessibleAction newInstance(String fieldName) {
            try {
                return new SetAccessibleAction(DefaultKafkaPrincipalBuilder.class.getDeclaredField(fieldName));
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Failed to install OAuthKafkaPrincipalBuilder. This Kafka version does not seem to be supported", e);
            }
        }
    }


    /**
     * Create a new instance
     */
    public OAuthKafkaPrincipalBuilder() {
        super(null, null);
    }



    @Override
    public void configure(Map<String, ?> configs) {

        Object sslPrincipalMappingRules = configs.get(BrokerSecurityConfigs.SSL_PRINCIPAL_MAPPING_RULES_CONFIG);
        Object sslPrincipalMapper;


        @SuppressWarnings("unchecked")
        List<String> principalToLocalRules = (List<String>) configs.get(BrokerSecurityConfigs.SASL_KERBEROS_PRINCIPAL_TO_LOCAL_RULES_CONFIG);
        String defaultRealm;
        Object kerberosShortNamer;


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

            try {
                defaultRealm = new KerberosPrincipal("tmp", 1).getRealm();
            } catch (Exception ex) {
                defaultRealm = "";
            }

            if (principalToLocalRules != null) {
                kerberosShortNamer = KerberosShortNamer.fromUnparsedRules(defaultRealm, principalToLocalRules);
                SET_KERBEROS_SHORT_NAMER.invoke(this, kerberosShortNamer);
            }


        } catch (RuntimeException
                | ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException e) {
            throw new IllegalStateException("Failed to initialize OAuthKafkaPrincipalBuilder", e);
        }
    }

    @Override
    public KafkaPrincipal build(AuthenticationContext context) {
        if (context instanceof SaslAuthenticationContext) {
            SaslServer saslServer = ((SaslAuthenticationContext) context).server();
            if (saslServer instanceof OAuthBearerSaslServer) {
                OAuthBearerSaslServer server = (OAuthBearerSaslServer) saslServer;
                if (OAuthBearerLoginModule.OAUTHBEARER_MECHANISM.equals(server.getMechanismName())) {
                    BearerTokenWithPayload token = (BearerTokenWithPayload) server.getNegotiatedProperty("OAUTHBEARER.token");
                    Services.getInstance().getSessions().put(token);

                    return new OAuthKafkaPrincipal(KafkaPrincipal.USER_TYPE,
                            server.getAuthorizationID(), token);
                }
            } else if (saslServer instanceof PlainSaslServer) {
                PlainSaslServer server = (PlainSaslServer) saslServer;

                // if PLAIN mechanism is used to communicate the OAuth token
                Principals principals = Services.getInstance().getPrincipals();

                // If the principal has already been stored in Principals, don't take it from Credentials
                OAuthKafkaPrincipal principal = (OAuthKafkaPrincipal) principals.getPrincipal(saslServer);
                if (principal != null) {
                    return principal;
                }

                // Take it from Credentials - it shuld be present there, and store it in Principals for any subsequent use
                principal = (OAuthKafkaPrincipal) Services.getInstance().getCredentials().takeCredentials(server.getAuthorizationID());
                if (principal != null) {
                    principals.putPrincipal(saslServer, principal);
                    return principal;
                }
            }
        }

        return super.build(context);
    }


    @Override
    public byte[] serialize(KafkaPrincipal principal) {
        if (principal instanceof OAuthKafkaPrincipal) {
            DefaultPrincipalData data = new DefaultPrincipalData()
                    .setType(principal.getPrincipalType())
                    .setName(principal.getName())
                    .setTokenAuthenticated(principal.tokenAuthenticated());
            BearerTokenWithPayload token = ((OAuthKafkaPrincipal) principal).getJwt();
            if (token instanceof BearerTokenWithJsonPayload) {
                try {
                    data.unknownTaggedFields().add(new RawTaggedField(OAUTH_DATA_TAG, new BearerTokenWithJsonPayload.Serde().serialize((BearerTokenWithJsonPayload) token)));
                } catch (IOException e) {
                    throw new SerializationException("Failed to serialize OAuthKafkaPrincipal", e);
                }
            }

            return MessageUtil.toVersionPrefixedBytes(DefaultPrincipalData.HIGHEST_SUPPORTED_VERSION, data);
        }
        return super.serialize(principal);
    }

    @Override
    public KafkaPrincipal deserialize(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        short version = buffer.getShort();
        if (version < DefaultPrincipalData.LOWEST_SUPPORTED_VERSION || version > DefaultPrincipalData.HIGHEST_SUPPORTED_VERSION) {
            throw new SerializationException("Invalid principal data version " + version);
        }

        DefaultPrincipalData data = new DefaultPrincipalData(new ByteBufferAccessor(buffer), version);
        List<RawTaggedField> unknownFields = data.unknownTaggedFields();
        if (unknownFields.size() > 0) {
            RawTaggedField field = unknownFields.get(0);
            if (field.tag() == OAUTH_DATA_TAG) {
                try {
                    OAuthKafkaPrincipal result = new OAuthKafkaPrincipal(data.type(), data.name(), new BearerTokenWithJsonPayload.Serde().deserialize(field.data()));
                    result.tokenAuthenticated(data.tokenAuthenticated());
                    return result;
                } catch (IOException e) {
                    throw new SerializationException("Failed to de-serialize OAuthKafkaPrincipal", e);
                }
            }
        }
        return new KafkaPrincipal(data.type(), data.name(), data.tokenAuthenticated());
    }
}
