Strimzi Kafka Image with SNAPSHOT Strimzi Kafka OAuth
=====================================================

This is a build of a Docker image based on `quay.io/strimzi/kafka:latest-kafka-2.6.0` with added most recently locally built SNAPSHOT version of Strimzi Kafka OAuth libraries.

This image adds a `/opt/kafka/libs/oauth` directory, and copies the latest jars for OAuth support in it.
Then it puts this directory as the first directory on the classpath.

The result is that the most recent Strimzi Kafka OAuth jars and their dependencies are used, because they appear on the classpath before the ones that are part of `quay.io/strimzi/kafka:latest-kafka-2.6.0` which are located in the `/opt/kafka/libs` directory.


Building
--------

Use `docker build` to build the image:

    docker build -t strimzi/kafka:latest-kafka-2.4.0-oauth .

You can choose a different tag if you want.

Also, take a look at Dockerfile:

    less Dockerfile
    
Note the `FROM` directive in the first line. It uses image coordinates to the latest publicly available Strimzi Kafka 2.4.0 image.

You may want to adjust this to a different public image, or to one manually built previously and is only available in your private Docker Registry.

For example, if you want to base your image on Strimzi Kafka 2.3.1 use `FROM strimzi/kafka:latest-kafka-2.3.1`.


Validating
----------

You can start an interactive shell container and confirm that the jars are there.

    docker run --rm -ti strimzi/kafka:latest-kafka-2.4.0-oauth /bin/sh
    ls -la libs/oauth/
    echo "$CLASSPATH"
    
If you want to play around more within the container you may need to make yourself `root`.

You achieve that by running the docker session as `root` user:

    docker run --rm -ti --user root strimzi/kafka:latest-kafka-2.4.0-oauth /bin/sh



Pushing the image to a Docker Repository
--------------------------------------

For Kubernetes to be able to use our image it needs to be pushed to either a public repository or to the private Docker Repository used by your Kubernetes distro.

For example if you are using Kubernetes Kind as described in [HACKING.md](../../../HACKING.md) then your Docker Repository is listening on port 5000 of your local ethernet IP.

    # On MacOS
    export REGISTRY_IP=$(ifconfig en0 | grep 'inet ' | awk '{print $2}') && echo $REGISTRY_IP 

    # On Linux
    #export REGISTRY_IP=$(ifconfig docker0 | grep 'inet ' | awk '{print $2}') && echo $REGISTRY_IP 

    export DOCKER_REG=$REGISTRY_IP:5000
    
You need to retag the built image before so you can push it to Docker Registry:

    docker tag strimzi/kafka:latest-kafka-2.4.0-oauth $DOCKER_REG/strimzi/kafka:latest-kafka-2.4.0-oauth
    docker push $DOCKER_REG/strimzi/kafka:latest-kafka-2.4.0-oauth

Actually, Kubernetes Kind supports an even simpler option how to make an image available to Kubernetes:

    kind load docker-image strimzi/kafka:latest-kafka-2.4.0-oauth 

Deploying
---------

In order for the operator to use your Kafka image, you have to replace the Kafka image coordinates in `install/cluster-operator/050-Deployment-strimzi-cluster-operator.yaml` in your `strimzi-kafka-operator` project.

This image is based on `strimzi/kafka:latest-kafka-2.4.0`, so we need to replace all occurrences of that with the proper coordinates to our image:

    sed -Ei 's#strimzi/kafka:latest-kafka-2.4.0#strimzi/kafka:latest-kafka-2.4.0-oauth#g' install/cluster-operator/050-Deployment-strimzi-cluster-operator.yaml

You also have to push the image to the Docker Registry trusted by your Kubernetes cluster, and you need to adjust `050-Deployment-strimzi-cluster-operator.yaml` for changed coordinates due to that.

For example:
```
sed -Ei -e "s#(image|value): strimzi/([a-z0-9-]+):latest#\1: ${DOCKER_REG}/strimzi/\2:latest#" \
  -e "s#([0-9.]+)=strimzi/([a-zA-Z0-9-]+:[a-zA-Z0-9.-]+-kafka-[0-9.]+)#\1=${DOCKER_REG}/strimzi/\2#" \
  install/cluster-operator/050-Deployment-strimzi-cluster-operator.yaml
```

It's best to check the `050-Deployment-strimzi-cluster-operator.yaml` file manually to make sure everything is in order:

    less install/cluster-operator/050-Deployment-strimzi-cluster-operator.yaml


You can now deploy Strimzi Kafka Operator following instructions in [HACKING.md](../../../HACKING.md)

