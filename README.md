Strimzi Kafka OAuth Guide
=========================


Strimzi Kafka OAuth modules provide support for using OAuth2 as authentication mechanism when establishing a session with Kafka Broker.


OAuth2 for Authentication
-------------------------

One of the advantages of OAuth2 compared to direct client-server authentication is that client credentials are never shared with the server. Rather, 
there exists a separate OAuth2 authorization server that application clients and application servers communicate with. This way user management, 
authentication, and authorization is 'outsourced' to authorization server.  

User authentication is separated into an outside step which user manually performs to obtain an access token or a refresh token, which grants a 
limited set of permissions to a specific client app. In the simplest case, the client application can authenticate in its own name using client 
credentials. While the client secret is in this case packaged with application client, the benefit is still that it is not shared with application 
server (Kafka Broker in our case) - the client first performs authentication against OAuth2 authorization server in exchange for an access token, 
which it then sends to authorization server instead of its secrets. Access tokens can be independently tracked and revoked at will, and represent a 
limited access to resources on application server.

When user authenticates and authorizes application client in exchange for a token, the client application can be packaged with only the access token
or the refresh token. User's username and password are never packaged with application client. The access token is sent directly to Kafka Broker 
during session initialisation, while a refresh token is first used to ask authorisation server for a new access token, before sending it to Kafka 
Broker to start a new authenticated session.

A developer authorising the client application with access to Kafka resources will access authorisation server directly using a web 
based or CLI based tool to sign in and provide access token or refresh token for application client.


When using refresh tokens for authentication the retrieved access tokens can be relatively short-lived which puts a time limit on potential abuse 
of a leaked access token. Repeated exchanges with authorisation server also provide centralised tracking of authentication attempts which is 
something that can be desired.

As hinted above, OAuth2 allows for different implementations, multiple layers of security, and it is up to you, taking existing security policies 
into account, how exactly you would implement it.


OAuth2 for Authorization
------------------------
Authentication is the procedure of establishing if the user is who they claim they are. Authorization is the procedure to decide if the user is 
allowed to perform some action using some resource. Kafka Brokers by default use an ACL based mechanism where access rules are saved in ZooKeeper 
and replicated across brokers. 

Authorization in Kafka is implemented completely separately and independently of authentication. Thus, it is possible to configure Kafka Brokers 
to use OAuth2 based authentication, and at the same time the default ACL authorization. 

Since OAuth2 access tokens can contain arbitrary claims - including sets of roles or permissions, rather than basing authorization decisions purely 
on user's identity, it could be based on externally assigned roles. All we need is define a mapping between access token claims and actions performed 
on resources. For example, a 'kafka-topic:clicks_*:consumer' claim can be interpreted to mean that the user can read from any topic of which name 
starts with 'clicks_'.

Another possibility is to delegate authorization decisions to external authorization service - this can be a service provided by the same 
OAuth2 authorization server used for authentication or it can be a different one, sharing the users database. 
See https://docs.kantarainitiative.org/uma/wg/oauth-uma-grant-2.0-09.html for OAuth2 based standard for standardised UMA authorization services.

At this time Strimzi Kafka OAuth doesn't provide authorization.



Kafka OAuth2 Support
--------------------
Kafka comes with basic OAuth2 support in the form of SASL based authentication module which provides client-server retrieval, exchange and validation 
of access token as credentials. For real world usage, extensions have to be provided in the form of JAAS callback handlers which is what Strimzi Kafka 
OAuth does.


Configuring the Kafka Broker as a Server
----------------------------------------
- Enable OAUTHBEARER as SASL mechanism
- Configure `io.strimzi.kafka.oauth.server.JaasServerOauthValidatorCallbackHandler` as validator callback handler
- Specify `admin.user` - in order to properly integrate with default ACL authorization implementation, the broker authenticates itself as _admin_ user.
With OAuth2 every access token is associated with some user via unique user identifier. `admin.user` should be set to the principal kafka-broker 
authenticates as. 

Specify valid issuer uri (OAUTH_VALID_ISSUER_URI) - only access tokens issued by this issuer will be accepted.

Next, if your authorization server issues JWT access tokens, and provides a JWKS certificates endpoint, you can configure that for fast local token 
validation (OAUTH_JWKS_ENDPOINT_URI). You can control how often JWKS certificates are refreshed by setting OAUTH_JWKS_REFRESH_SECONDS, and you can set
the maximum time JWKS certificates are considered valid (OAUTH_JWKS_EXPIRY_SECONDS).

TODO: Alternatively, you can also manually retrieve the public key from your authorization server, and configure it explicitly (OAUTH_JWKS_CERT).


If you're using non-JWT opaque access tokens, then you have to configure an introspection endpoint (OAUTH_INTROSPECTION_ENDPOINT_URI), and token 
validation will have to be delegated to your authorization server.

Kafka broker also has to establish its own identity when connecting to other brokers. If OAUTHBEARER is used as inter-broker protocol then you need
to provide client configuration as described in the next chapter.


Configuring the Kafka Broker as a Client
----------------------------------------
- Enable OAUTHBEARER as interbroker SASL mechanism

Provide Kafka Broker credentials in the same way as with any Kafka Client as described in the next chapter.


Configuring the Kafka Client or Kafka Broker as a Client
--------------------------------------------------------
- Provide jaas configuration - specify `org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule` as a login module.
- Configure login callback handler class - `io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler`
- Configure authorization server token endpoint (OAUTH_TOKEN_ENDPOINT_URI)
- Specify credentials for authentication

Three mechanisms are currently supported:

a) Authenticate with client id and client secret
  - specify OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET
  
b) Authenticate with access token obtained from authorization server 
  - specify OAUTH_ACCESS_TOKEN

c) Authenticate with refresh token obtained from authorization server
  - specify OAUTH_REFRESH_TOKEN
  - specify OAUTH_CLIENT_ID
  - Optionally specify OAUTH_CLIENT_SECRET depending on whether the client used is a public client or confidential client



Obtaining the tokens
--------------------

This step is specific to the authorization server that you use. 

#### Keycloak


Keycloak server distribution comes with a java based CLI client tool called 'kcadm'.

An example of logging into Keycloak server as user 'alice', authorizing specific client with 'kafka-producer' clientId, and some secret (we assume 
here the `demo` realm as prepared in `docker/keycloak/import`):


    bin/kcadm.sh config credentials --server http://${KEYCLOAK_IP}:8080/auth --realm demo --client kafka-consumer-client --secret kafka-consumer-client-secret --user alice --password alice-password

If using demo docker-compose setup you can invoke kcadm.sh from `keycloak` container by first executing the following:

    docker exec -ti keycloak /bin/bash
    export KEYCLOAK_IP=YOUR INTRANET IP
    
Now you can run bin/kcadm.sh ...

The access token and refresh token are saved in ~/.keycloak/kcadm.config file. An access token or refresh token can be obtained manually from that file.


TODO:

A more convenient option would be for kcadm.sh to be able to return the token for current session.

For example:

    bin/kcadm.sh config credentials --server http://${KEYCLOAK_IP}:8080/auth --realm demo --client kafka-consumer-client --user alice --password alice-password --token


Or:

    bin/kcadm.sh config credentials get-token
    bin/kcadm.sh config credentials get-refresh-token




Building
--------

mvn clean install


Demo
----

The demo uses docker-compose build projects to start Keycloak (`docker/keycloak`), import clients, and users as a new Keycloak realm (`docker/keycloak-import`), 
package containerized Kafka with strimzi-kafka-oauth modules and example configuration (`docker/kafka-oauth-*`).

Follow the instructions in [docker/README.md](docker/README.md) to start up the demo servers.

Then, follow instructions in [examples/README.md](examples/README.md) to run example producer, and consumer.


TODOs
-----

We're preparing more OAuth2 servers to run the demo with - Hydra, maybe Okta, CAS, a Spring Boot Kafka client example.

Strimzi Kafka docker image support is still work in progress. Confluent image requires some polish.

We need a working example of default Kafka ACL Authorizer.

We need a simple implementation of JWT claims based Authorizer.

