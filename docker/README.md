
First run:
 
    mvn clean install -f ..

To start from clean slate, make sure any old saved containers are removed:

    docker rm -f kafka zookeeper keycloak
    
Then run docker-compose command. 

But first, determine your machine's local network IP address, and set it as env variable.

    export KEYCLOAK_IP=<YOUR_IP_ADDRESS>

For example, on macOS:

    export KEYCLOAK_IP=$(ifconfig en0 | grep 'inet ' | awk '{print $2}')

When client connects to Kafka Broker running inside docker image, the broker will redirect the client to: kafka:9292.
For that reason you also have to put the following line in your `/etc/hosts`:

    127.0.0.1       kafka

On macOS or Windows you can also use:

    export KEYCLOAK_IP=host.docker.internal

In this case you also have to add `host.docker.internal` (in addition to `kafka`) to your `/etc/hosts` file:

    127.0.0.1       host.docker.internal
  
The reason is that Kafka Brokers, and Kafka clients connecting to Keycloak have to use the same hostname to ensure 
compatibility of generated access tokens.


All the following docker-compose commands should be run from this directory.


You can startup all the containers at once:

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml -f keycloak/compose.yml -f keycloak-import/compose.yml up --build

Or, you can have multiple terminal windows and start individual component in each
(make sure to set KEYCLOAK_IP env variable in each terminal):

    docker-compose -f compose.yml -f kafka-oauth-strimzi/compose.yml up --build 

    docker-compose -f keycloak/compose.yml up

    docker-compose -f compose.yml -f keycloak-import/compose.yml up --build


See [examples/README.md](../examples/README.md) for more information.


TODO: Implement and document simple OAuth JWT Authorizer