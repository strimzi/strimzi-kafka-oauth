Testsuite
=========

This module contains integration tests for OAuth 2.0 support - different configurations for client and server are tested.

The tests in this testsuite are all integration tests, making use of Arquillian Cube to start and stop the necessary docker 
containers. The tests are bootstrapped through standard maven's 'test' phase, rather than the conventional 'integration-test' 
phase which is otherwise used when integration tests are intermingled in the same project with unit tests. 


Preparing
=========

Make sure that the following ports on your host machine are free: 9092, 2181 (Kafka), 8080, 8443 (Keycloak), 4444, 4445 (Hydra).

Then, you have to add some entries to your `/etc/hosts` file:

    127.0.0.1            keycloak
    127.0.0.1            hydra
    127.0.0.1            kafka

That's needed for host resolution, because Kafka brokers and Kafka clients connecting to Keycloak / Hydra have to use the 
same hostname to ensure compatibility of generated access tokens.

Also, when Kafka client connects to Kafka broker running inside docker image, the broker will redirect the client to: kafka:9292.


Running
=======

You may first need to perform the following cleanup of pre-existing containers / network definitions:

    docker rm -f kafka zookeeper keycloak hydra
    docker network rm $(docker network ls | grep test | awk '{print $1}')
    
To build and run the testsuite you need a running 'docker' daemon, then simply run:

    mvn clean install

Or if you are in strimzi-kafka-oauth project root directory:

    mvn clean install -f testsuite

By using `clean` you make sure that the latest project jars are included into the kafka image.

There are several profiles available to test with a specific version of Kafka images:

- kafka-2_3_0
- kafka-2_4_0
- kafka-2_4_1
- kafka-2_5_0

Only one at a time can be applied. For example:
 
    mvn clean install -f testsuite -Pkafka-2_4_1

If you want to run only a single test, you first have to build the whole testsuite:

    mvn clean install -f testsuite -DskipTests
    mvn test -f testsuite/client-secret-jwt-keycloak-test

Troubleshooting
===============

### Network is ambiguous

An example error message:

    com.github.dockerjava.api.exception.BadRequestException: {"message":"network client-secret-jwt-keycloak-test_default is ambiguous (2 matches found on name)"}

In case of a failed test Arquillian Cube sometimes fails to automatically remove the docker network it created.

You can list existing networks with:

    docker network ls

And remove the networks that shouldn;t be there with:

    docker rm NETWORK_ID

You can delete all test networks at once by running the following:

    docker network rm $(docker network ls | grep test | awk '{print $1}')


### Container name already in use

An example error message:

    Caused by: com.github.dockerjava.api.exception.ConflictException: {"message":"Conflict. The container name \"/keycloak\" is already in use by container \"ec9246b84b811e6fdc5224336bb95b54393d793b725cc9d764499d1df0927d72\". You have to remove (or rename) that container to be able to reuse that name."}

Run the following to remove any left-over containers:

    docker rm -f kafka zookeeper keycloak hydra

If this fails, and you see 'Cannot remove ... Permission Denied' in `dockerd` log on Linux, you may have issues with AppArmor service.

On Ubuntu you can follow [these instructions](https://bugs.launchpad.net/ubuntu/+source/snapd/+bug/1803476/comments/21) to disable it.

But then you may need to re-enable it again:
    
    sudo systemctl enable apparmor.service --now

Also remove any docker test networks that are left due to error as instructed in previous section ('Network is ambiguous') and rerun the test. 
Arquillian Cube automatically fixes the container name conflict by removing old containers.

You can check that no container by the same name exists by doing:

    docker ps -a | grep CONTAINER_NAME


### Could not auto start container

If you see the warning 'Docker Image not on DockerHost and it is going to be automatically pulled.', then the failure to start container may be due to the necessary images still being pulled.

Remove any docker test networks that are left as described in 'Network is ambiguous' issue.

Then try to repeat the test run, using `-rf` option to skip successful tests as advised by maven error output.


### Could not build image - Permission denied

Example message:

    Could not build image: java.util.concurrent.ExecutionException: com.spotify.docker.client.shaded.javax.ws.rs.ProcessingException: java.io.IOException: Permission denied

If you're running Docker daemon as root on Linux, you may need to configure an extra listener for TCP and set DOCKER_HOST env variable.

For example, to run docker daemon use:

    sudo dockerd -H tcp://127.0.0.1:2375 -H unix:///var/run/docker.sock

To set environment you then use:

    export DOCKER_HOST=tcp://127.0.0.1:2375


### Couldn't resolve server

Make sure that you added 'kafka', 'keycloak', and 'hydra' to your `/etc/hosts` as follows:

    127.0.0.1    kafka
    127.0.0.1    keycloak
    127.0.0.1    hydra
