Examples / Demo
===============

This directory contains a preconfigured demo which uses Keycloak as OAuth2 authorization server.

The demo uses docker-compose build projects to start Keycloak (`docker/keycloak`), import clients, and users as a new Keycloak realm (`docker/keycloak-import`), package containerized Kafka with strimzi-kafka-oauth modules and example configuration (`docker/kafka-oauth-strimzi`).

The demo is primarily about authentication, and does not enforce any restrictions on clients.

First, start the necessary containers by following instructions in [docker/README.md](docker/README.md)

Then, follow instructions in [producer README](producer/README.md) and [consumer README](consumer/README.md) to run example clients.

Also, don't forget to set all the environment variables as instructed, and add 'keycloak' to your /etc/hosts if necessary.

You can use an IDE to run example clients, or you can run from shell.

You can remove the need for specifying client id and client secret in client configuration by generating an access token directly, using CLI tooling.

Assuming `keycloak` container is up and running you can do the following.

Connect to 'keycloak' container to get access to `kcadm.sh` tool:

    docker exec -ti keycloak /bin/sh
    cd /opt/jboss/keycloak

Set the server endpoint url:

    export URL=http://keycloak:8080/auth
    
If you are using SSL configuration with Keycloak (using `compose-ssl.yml`) - which you should, whenever running anywhere but on your local machine - use the following endpoint url:
    
    export URL=https://keycloak:8443/auth

If you're using SSL, configure the truststore for client to be able to connect:

    bin/kcadm.sh config truststore --trustpass changeit standalone/configuration/keycloak.server.keystore.p12

Note: Unfortunately `kcadm.sh` doesn't support turning off certificate hostname verification, which means that for SSL example demo we can't generate tokens with proper issuer.
    
Now, you can authenticate as client application itself:

    bin/kcadm.sh config credentials --server $URL --realm demo --client kafka-producer-client --secret kafka-producer-client-secret

Alternatively, you can authenticate as some user ('alice' in this case) authorizing the client application, which may add user-specific permissions to the token:

    bin/kcadm.sh config credentials --server $URL --realm demo --user alice --password alice-password --client kafka-producer-client --secret kafka-producer-client-secret
    
After successfully authenticating you can retrieve access token and refresh token from local config file:

    # retrieve saved access token
    cat ~/.keycloak/kcadm.config | grep token | awk -F'"' '{print $4}'
    
    # OR retrieve a refresh token
    cat ~/.keycloak/kcadm.config | grep refreshToken | awk -F'"' '{print $4}'

For this example you can instruct ExampleProducer / ExampleConsumer to use an access token by setting OAUTH_ACCESS_TOKEN env variable:

    export OAUTH_ACCESS_TOKEN=<YOUR ACCESS TOKEN>
    
Or, to use a refresh token, set OAUTH_REFRESH_TOKEN env variable (if OAUTH_ACCESS_TOKEN is set it will be used instead):

    export OAUTH_REFRESH_TOKEN=<YOUR REFRESH TOKEN>
 
When you authenticate as client application itself (`kafka-producer-client`), you get an access token that looks something like:

```
{
  "jti":"5f451020-a460-49c2-8750-5e9f24b359c8",
  "exp":1567409778,
  "nbf":0,
  "iat":1567373778,
  "iss":"http://192.168.64.103:8080/auth/realms/demo",
  "sub":"f996fea8-0958-4e3a-8eee-3e61949e627b",
  "typ":"Bearer",
  "azp":"kafka-producer-client",
  "auth_time":0,
  "session_state":"e2b5bb66-134d-4470-80df-c0258d9ff9a1",
  "acr":"1",
  "scope":"email profile",
  "clientId":"kafka-producer-client",
  "clientHost":"172.24.0.1",
  "email_verified":false,
  "preferred_username":"service-account-kafka-producer-client",
  "clientAddress":"172.24.0.1",
  "email":"service-account-kafka-producer-client@placeholder.org"
}
```

This client is configured in the demo in such a way that it gets no `roles` claims by itself.

When authenticating as user `alice`, some roles will be added to the token, which may be used by custom Authorizer in Kafka Broker to grant access to some topics:

```
{
  "jti":"085bd123-e93e-4301-95ab-b1efca7f19e2",
  "exp":1567410424,
  "nbf":0,
  "iat":1567374424,
  "iss":"http://192.168.64.103:8080/auth/realms/demo",
  "aud":"kafka-broker",
  "sub":"062eb69a-8e18-4937-9b23-d6c158d43829",
  "typ":"Bearer",
  "azp":"kafka-producer-client",
  "auth_time":0,
  "session_state":"601e4d3f-6808-4e5f-b6a6-9bf790a85ff7",
  "acr":"1",
  "resource_access":
  {
    "kafka":
    {
      "roles":["kafka-topic:superapp_*:owner"]
    }
  },
  "scope":"email profile",
  "email_verified":false,
  "preferred_username":"alice",
  "email":"alice@example.com"
}
```

Another client application - `kafka-consumer-client` already gets some `roles` claims by itself.
When you authenticate as `kafka-consumer-client` you get access token that looks something like:

```
{
  "jti":"be476144-06a5-483e-bbf1-f6a9c83c8174",
  "exp":1599516353,
  "nbf":0,
  "iat":1567375553,
  "iss":"http://192.168.64.103:8080/auth/realms/demo",
  "aud":"kafka-broker",
  "sub":"89307c94-655f-424f-b709-f873fec63dcc",
  "typ":"Bearer",
  "azp":"kafka-consumer-client",
  "auth_time":0,
  "session_state":"b34ff50f-9daa-4a37-8878-06cf6b7430b8",
  "acr":"1",
  "resource_access":
  {
    "kafka":
    {
      "roles":["kafka-topic:superapp_*:consumer"]
    }
  },
  "scope":"email profile",
  "clientHost":"172.24.0.1",
  "email_verified":false,
  "clientId":"kafka-consumer-client",
  "preferred_username":"service-account-kafka-consumer-client",
  "clientAddress":"172.24.0.1",
  "email":"service-account-kafka-consumer-client@placeholder.org"
}

```

By default, no authorizer is configured which grants all permissions to all clients.

You can configure the default authorizer using KAFKA_AUTHORIZER_CLASS_NAME env variable in `kafka-oauth-*/compose.yml`. 

For example:

    KAFKA_AUTHORIZER_CLASS_NAME: kafka.security.auth.SimpleAclAuthorizer
    KAFKA_SUPER_USERS: User:service-account-kafka-broker

    # username extraction from JWT token claim
    OAUTH_USERNAME_CLAIM: preferred_username

When running Kafka with this configuration, the ExampleProducer will not have the necessary permissions by default.
Also, no user has any permissions by default when SimpleAclAuthorizer is enabled - except the super users.
SimpleAclAuthorizer only takes established user identity into account (it has no integration with OAuth tokens, so above mentioned differences in roles present in JWT token - those make no difference to SimpleAclAuthorizer.

In order to add permissions you then have to use command line tool that comes with kafka, to add permissions to specific users.
For example:

    docker exec -ti kafka /bin/sh
    
    # Topic 'Topic1' does not exist yet, but we can already grant access on it
    bin/kafka-acls.sh --topic Topic1 --producer --authorizer-properties zookeeper.connect=zookeeper:2181 --add --allow-principal User:alice

In our demo you then have to make sure that the access token created by `alice` is provided for ExampleProducer's authentication.
