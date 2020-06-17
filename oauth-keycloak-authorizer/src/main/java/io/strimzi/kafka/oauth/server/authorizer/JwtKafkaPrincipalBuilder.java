/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server.authorizer;

import io.strimzi.kafka.oauth.server.services.StrimziKafkaPrincipalBuilder;

/**
 * This class needs to be enabled as the PrincipalBuilder on Kafka Broker.
 * <p>
 * It ensures that the OAuthBearerToken produced by <em>io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler</em>
 * is available to KeycloakRBACAuthorizer.
 * </p>
 * <p>
 * You can use 'principal.builder.class=io.strimzi.kafka.oauth.server.authorizer.JwtKafkaPrincipalBuilder'
 * property definition in server.properties to install it.
 * </p>
 *
 * @deprecated Use <em>io.strimzi.kafka.oauth.server.services.StrimziKafkaPrincipalBuilder</em> class instead.
 */
@Deprecated
public class JwtKafkaPrincipalBuilder extends StrimziKafkaPrincipalBuilder {
}