## Token Based Authorization with Keycloak Authorization Services

Once the Kafka Broker has obtained an access token by using Strimzi Kafka OAuth for authentication, it is possible to use centrally managed authorization rules to enforce access restrictions onto Kafka Clients.
For this Strimzi Kafka OAuth supports the use of `Keycloak Authorization Services`.

A custom authorizer has to be configured on the Kafka Broker to take advantage of Authorization Services REST endpoints available on Keycloak, which provide a list of granted permissions on resources for authenticated users.
The list is fetched once, and enforced locally on the Kafka Broker for each user session in order to provide fast authorization decisions.


## Building the Example Project

Before using the example, we first need to build the project, and prepare resources.

First change the current directory to `examples/docker`:

    cd examples/docker

Now build the project, and prepare resources:

    mvn clean install -f ../..
    mvn clean install

We are now ready to start up the containers and see `Keycloak Authorization Services` in action.


## Starting Up the Containers

First make sure any existing containers with the same name are removed, otherwise we might use previous configurations:

    docker rm keycloak kafka zookeeper
    
Let's start up all the containers with authorization configured, and we'll then perform any manual step, and explain how everything works.

    docker-compose -f compose.yml -f keycloak/compose.yml -f keycloak-import/compose.yml \
      -f kafka-oauth-strimzi/compose-authz.yml up --build

When everything starts up without errors we should have one instance of `keycloak` listening on localhost:8080.


## Using Keycloak Admin Console to Configure Authorization
 
You can login to the Admin Console by opening `http://localhost:8080/auth/admin` and using `admin` as both username, and a password.

In the upper left corner under the Keycloak icon you should see `Master` selected as a current realm.
Moving the mouse pointer over it should reveal two additional realms - `Demo` and `Kafka-authz`.

For this example we are interested in the `kafka-authz` realm.
Selecting it will open the `Realm Settings` for the `kafka-authz` realm.
Next to `Realm Settings` there are other sections we are interested in - `Groups`, `Roles`, `Clients` and `Users`.

Under `Groups` we can see several groups that can be used to mark users as having some permissions.
Groups are sets of users with name assigned. Typically they are used to geographically or organisationally compartmentalize users into organisations, organisational units, departments etc.

In Keycloak the groups can be stored in an LDAP identity provider.
That makes it possible to make some user a member of some group - through a custom LDAP server admin UI for example, which grants them some permissions on Kafka resources.

Under `Users`, click on the `View all users` button and you will see two users defined - `alice` and `bob`. `alice` is a member of the `ClusterManager Group`, and `bob` is a member of `ClusterManager-cluster2 Group`. 
In Keycloak the users can be stored in an LDAP identity provider.

Under `Roles` we can see several realm roles which can be used to mark users or clients as having some permissions.
Roles are a concept analogous to groups. They are usually used to 'tag' users as playing organisational roles and having permissions that pertain to it. 
In Keycloak the roles can not be stored in an LDAP identity provider - if that is your requirement then you should use groups instead.

Under `Clients` we can see some additional clients configured - `kafka`, `kafka-cli`, `team-a-client`, `team-b-client`.
The client with client id `kafka` is used by Kafka Brokers to perform the necessary OAuth2 communication for access token validation, 
and to authenticate to other Kafka Broker instances using OAuth2 client authentication.
This client also contains Authorization Services resource definitions, policies and authorization scopes used to perform authorization on the Kafka Brokers.

The client with client id `kafka-cli` is a public client that can be used by the Kafka command line tools when authenticating with username and password to obtain an access token or a refresh token.

Clients `team-a-client`, and `team-b-client` are confidential clients representing services with partial access to certain Kafka topics.

The authorization configuration is defined in the `kafka` client under `Authorization` tab.
This tab becomes visible when `Authorization Enabled` is turned on under the `Settings` tab.


## Authorization Services - Resources, Authorization Scopes, Policies and Permissions

`Keycloak Authorization Services` uses several concepts that together take part in defining, and applying access control to resources.

`Resources` define _what_ we are protecting from unauthorized access.
Each resource can contain a list of `authorization scopes` - actions that are available on the resource, so that permission on a resource can be granted for one or more actions only.
`Policies` define the groups of users we want to target with permissions. Users can be targeted based on group membership, assigned roles, or individually.
Finally, the `permissions` tie together specific `resources`, `action scopes` and `policies` to define that 'specific users U can perform certain actions A on the resource R'.

You can read more about `Keycloak Authorization Services` on [project's web site](https://www.keycloak.org/docs/latest/authorization_services/index.html).

If we take a look under the `Resources` sub-tab of `Authorization` tab, we'll see the list of resource definitions.
These are resource specifiers - patterns in a specific format, that are used to target policies to specific resources.
The format is quite simple. For example:

- `kafka-cluster:cluster-1,Topic:a_*`  ... targets only topics in kafka cluster 'cluster-1' with names starting with 'a_'

If `kafka-cluster:XXX` segment is not present, the specifier targets any cluster.

- `Group:x_*` ... targets all consumer groups on any cluster with names starting with 'x_'

The possible resource types mirror the [Kafka authorization model](https://kafka.apache.org/documentation/#security_authz_primitives) (Topic, Group, Cluster, ...).

Under `Authorization Scopes` we can see a list of all the possible actions (Kafka permissions) that can be granted on resources of different types.
It requires some understanding of [Kafka's permissions model](https://kafka.apache.org/documentation/#resources_in_kafka) to know which of these make sense with which resource type (Topic, Group, Cluster, ...).
This list mirrors Kafka permissions and should be the same for any deployment.

There is an [authorization-scopes.json](../oauth-keycloak-authorizer/etc/authorization-scopes.json) file containing the authorization scopes that can be imported, so that they don't have to be manually entered for every new `Authorization Services` enabled client.
In order to import `authorization-scopes.json` into a new client, first make sure the new client is `Authorization Enabled` and saved. Then, click on the `Authorization` tab and use the `Import` to import the file. Afterwards, if you select the `Authorization Scopes` you will see the loaded scopes.
For this example the authorization scopes have already been imported as part of the realm import.

Under the `Policies` sub-tab there are filters that match sets of users.
Users can be explicitly listed, or they can be matched based on the Roles, or Groups they are assigned.
Policies can even be programmatically defined using JavaScript where logic can take into account the context of the client session - e.g. client ip (that is client ip of the Kafka client).

Then, finally, there is the `Permissions` sub-tab, which defines 'role bindings' where `resources`, `authorization scopes` and `policies` are tied together to apply a set of permissions on specific resources for certain users.

Each `permission` definition can have a nice descriptive name which can make it very clear what kind of access is granted to which users.
For example:
    
    Dev Team A can write to topics that start with x_ on any cluster
        
    Dev Team B can read from topics that start with x_ on any cluster
    Dev Team B can update consumer group offsets that start with x_ on any cluster

    ClusterManager of cluster2 Group has full access to cluster config on cluster2
    ClusterManager of cluster2 Group has full access to consumer groups on cluster2
    ClusterManager of cluster2 Group has full access to topics on cluster2
    
If we take a closer look at the `Dev Team A can write ...` permission definition, we see that it combines a resource called `Topic:x_*`, scopes `Describe` and `Write`, and `Dev Team A` policy.
If we click on the `Dev Team A` policy, we see that it matches all users that have a realm role called `Dev Team A`.

Similarly, the `Dev Team B ...` permissions perform matching using the `Dev Team B` policy which also uses realm role to match allowed users - in this case those with realm role `Dev Team B`.
The `Dev Team B ...` permissions grant users `Describe` and `Read` on `Topic:x_*`, and `Group:x_*` resources, effectively giving matching users and clients the ability to read from topics, and update the consumed offsets for topics and consumer groups that have names starting with 'x_'. 

## Targeting Permissions - Clients and Roles vs. Users and Groups
  
In Keycloak, confidential clients with 'service accounts' enabled can authenticate to the server in their own name using a clientId and a secret.
This is convenient for microservices which typically act in their own name, and not as agents of a particular user (like a web site would, for example).
Service accounts can have roles assigned like regular users.
They can not, however, have groups assigned.
As a consequence, if you want to target permissions to microservices using service accounts, you can't use Group policies, but are forced to use Role policies.
Or, thinking about it another way, if you want to limit certain permissions only to regular user accounts where authentication with username and password is required, you should use Group policies, rather than Role policies.
That's what we see used in `permissions` that start with 'ClusterManager'.
Performing cluster management is usually done interactively - in person - using CLI tools. 
It makes sense to require the user to log-in, before using the resulting access token to authenticate to Kafka Broker.
In this case the access token represents the specific user, rather than the client application.


## Authorization in Action Using CLI Clients

Before continuing, there is one setting we need to check.
Due to [a little bug in Keycloak](https://issues.redhat.com/browse/KEYCLOAK-12640) the realm is at this point misconfigured, and we have to fix the configuration manually.
Under `Clients` / `kafka` / `Authorization` / `Settings` make sure the `Decision Strategy` is set to `Affirmative`, and NOT to `Unanimous`. Click `Save` after fixing it.
  
With configuration now in place, let's create some topics, use a producer, a consumer, and try to perform some management operations using different user and service accounts. 

First, we'll spin up a new docker container based on a Kafka image previously built by `docker-compose` which we'll use to connect to the already running Kafka Broker.

    docker run -ti --rm --name kafka-cli --network docker_default strimzi/example-kafka /bin/sh
    
Let's try to produce some messages as client `team-a-client`.
First, we prepare a Kafka consumer configuration file with authentication parameters.

```
cat > ~/team-a-client.properties << EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=OAUTHBEARER
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
  oauth.client.id="team-a-client" \
  oauth.client.secret="team-a-client-secret" \
  oauth.token.endpoint.uri="http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token" ;
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
EOF
```

In the Keycloak Console you can find which roles are assigned to the `team-a-client` service account, by selecting `team-a-client` in the `Clients` section.
and then opening the `Service Account Roles` tab for the client.
You should see the `Dev Team A` realm role assigned. 

We can now use this configuration with Kafka's CLI tools.

Make sure the necessary classes are on the classpath:

    export CLASSPATH=/opt/kafka/libs/strimzi/*:$CLASSPATH


### Producing Messages

Let's try produce some messages to topic 'my-topic':

```
bin/kafka-console-producer.sh --broker-list kafka:9092 --topic my-topic \
  --producer.config=$HOME/team-a-client.properties
First message
```

When we press `Enter` to push the first message we receive `Not authorized to access topics: [my-topic]` error.

`team-a-client` has a `Dev Team A` role which gives it permissions to do anything on topics that start with 'a_', and only write to topics that start with 'x_'.
The topic named `my-topic` matches neither of those.

Use CTRL-C to exit the CLI application, and let's try to write to topic `a_messages`.

```
bin/kafka-console-producer.sh --broker-list kafka:9092 --topic a_messages \
  --producer.config ~/team-a-client.properties
First message
Second message
```

Although we can see some unrelated warnings, looking at the Kafka container log there is DEBUG level output saying 'Authorization GRANTED'.

Use CTRL-C to exit the CLI application.


### Consuming Messages

Let's now try to consume the messages we have produced.

    bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic a_messages \
      --from-beginning --consumer.config ~/team-a-client.properties

This gives us an error like: `Not authorized to access group: console-consumer-55841`.

The reason is that we have to override the default consumer group name - `Dev Team A` only has access to consumer groups that have names starting with 'a_'.
Let's set custom consumer group name that starts with 'a_'

    bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic a_messages \
      --from-beginning --consumer.config ~/team-a-client.properties --group a_consumer_group_1

We should now receive all the messages for the 'a_messages' topic, after which the client blocks waiting for more messages.

Use CTRL-C to exit.


### Using Kafka's CLI Administration Tools

Let's now list the topics:

    bin/kafka-topics.sh --bootstrap-server kafka:9092 --command-config ~/team-a-client.properties --list
    
We get one topic listed: `a_messages`.

Let's try and list the consumer groups:

    bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
      --command-config ~/team-a-client.properties --list

Similarly to listing topics, we get one consumer group listed: `a_consumer_group_1`.

There are more CLI administrative tools. For example we can try to get the default cluster configuration:

    bin/kafka-configs.sh --bootstrap-server kafka:9092 --command-config ~/team-a-client.properties \
      --entity-type brokers --describe --entity-default

But that will fail with `Cluster authorization failed.` error, because this operation requires cluster level permissions which `team-a-client` does not have.


### Client with Different Permissions

Let's prepare a configuration for `team-b-client`:

```
cat > ~/team-b-client.properties << EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=OAUTHBEARER
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
  oauth.client.id="team-b-client" \
  oauth.client.secret="team-b-client-secret" \
  oauth.token.endpoint.uri="http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token" ;
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
EOF
```

If we look at `team-b-client` client configuration in Keycloak, under `Service Account Roles` we can see that it has `Dev Team B` realm role assigned.
Looking in Keycloak Console at the `kafka` client's `Authorization` tab where `Permissions` are listed, we can see the permissions that start with 'Dev Team B ...'.
These match the users and service accounts that have the `Dev Team B` realm role assigned to them. 
The `Dev Team B` users have full access to topics beginning with 'b_' on Kafka cluster `cluster2` (which is the designated cluster name of the demo cluster we brought up), and read access on topics that start with 'x_'.

Let's try produce some messages to topic `a_messages` as `team-b-client`:

```
bin/kafka-console-producer.sh --broker-list kafka:9092 --topic a_messages \
  --producer.config ~/team-b-client.properties
Message 1
```

We get `Not authorized to access topics: [a_messages]` error as we expected. Let's try to produce to topic `b_messages`:

```
bin/kafka-console-producer.sh --broker-list kafka:9092 --topic b_messages \
  --producer.config ~/team-b-client.properties
Message 1
Message 2
Message 3
```

This should work fine.

What about producing to topic `x_messages`. `team-b-client` is only supposed to be able to read from such a topic.

```
bin/kafka-console-producer.sh --broker-list kafka:9092 --topic x_messages \
  --producer.config ~/team-b-client.properties
Message 1
```

We get a `Not authorized to access topics: [x_messages]` error as we expected. 
Client `team-a-client`, on the other hand, should be able to write to such a topic:

```
bin/kafka-console-producer.sh --broker-list kafka:9092 --topic x_messages \
  --producer.config ~/team-a-client.properties
Message 1
```

However, we again receive `Not authorized to access topics: [x_messages]`. What's going on?
The reason for failure is that while `team-a-client` can write to `x_messages` topic, it does not have a permission to create a topic if it does not yet exist.

We now need a power user that can create a topic with all the proper settings - like the right number of partitions and replicas.


### Power User Can Do Anything

Let's create a configuration for user `bob` who has full ability to manage everything on Kafka cluster `cluster2`.

First, `bob` will authenticate to Keycloak server with his username and password and get a refresh token.

```
export TOKEN_ENDPOINT=http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token
REFRESH_TOKEN=$(./oauth.sh -q bob)
```

This will prompt you for a password. Type 'bob-password'.

We can inspect the refresh token:

    ./jwt.sh $REFRESH_TOKEN

By default this is a long-lived refresh token that does not expire.

Now we will create the configuration file for `bob`:

```
cat > ~/bob.properties << EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=OAUTHBEARER
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required \
  oauth.refresh.token="$REFRESH_TOKEN" \
  oauth.client.id="kafka-cli" \
  oauth.token.endpoint.uri="http://keycloak:8080/auth/realms/kafka-authz/protocol/openid-connect/token" ;
sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler
EOF
```

Note that we use the `kafka-cli` public client for the `oauth.client.id` in the `sasl.jaas.config`. 
Since that is a public client it does not require any secret.
We can use this because we authenticate with a token directly (in this case a refresh token is used to request an access token behind the scenes which is then sent to Kafka broker for authentication, and we already did the authentication for the refresh token).


Let's now try to create the `x_messages` topic:

    bin/kafka-topics.sh --bootstrap-server kafka:9092 --command-config ~/bob.properties \
      --topic x_messages --create --replication-factor 1 --partitions 1

The operation should succeed (you can ignore the warning about periods and underscores).
We can list the topics:

    bin/kafka-topics.sh --bootstrap-server kafka:9092 --command-config ~/bob.properties --list

If we try the same as `team-a-client` or `team-b-client` we will get different responses.

    bin/kafka-topics.sh --bootstrap-server kafka:9092 --command-config ~/team-a-client.properties --list
    bin/kafka-topics.sh --bootstrap-server kafka:9092 --command-config ~/team-b-client.properties --list

Roles `Dev Team A`, and `Dev Team B` both have `Describe` permission on topics that start with 'x_', but they can't see the other team's topics as they don't have `Describe` permissions on them.

We can now again try to produce to the topic as `team-a-client`.

```
bin/kafka-console-producer.sh --broker-list kafka:9092 --topic x_messages \
  --producer.config ~/team-a-client.properties
Message 1
Message 2
Message 3
```

This works.

If we try the same as `team-b-client` it should fail.

```
bin/kafka-console-producer.sh --broker-list kafka:9092 --topic x_messages \
  --producer.config ~/team-b-client.properties
Message 4
Message 5
```

We get an error - `Not authorized to access topics: [x_messages]`.

But `team-b-client` should be able to consume messages from the `x_messages` topic:

    bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic x_messages \
      --from-beginning --consumer.config ~/team-b-client.properties --group x_consumer_group_b

Whereas `team-a-client` does not have permission to read, even though they can write:

    bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic x_messages \
      --from-beginning --consumer.config ~/team-a-client.properties --group x_consumer_group_a

We get a `Not authorized to access group: x_consumer_group_a` error.
What if we try to use a consumer group name that starts with 'a_'?

    bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic x_messages \
      --from-beginning --consumer.config ~/team-a-client.properties --group a_consumer_group_a
    
We now get a different error: `Not authorized to access topics: [x_messages]`

It just won't work - `Dev Team A` has no `Read` access on topics that start with 'x_'.

User `bob` should have no problem reading from or writing to any topic:

    bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic x_messages \
      --from-beginning --consumer.config ~/bob.properties

