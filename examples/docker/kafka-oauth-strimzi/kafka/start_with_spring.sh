#!/bin/bash
set -e

source functions.sh

#URI=${SPRING_URI}
#if [ "" == "${URI}" ]; then
#    URI="http://${SPRING_HOST:-spring}:8080"
#fi

#wait_for_url $URI "Waiting for Spring Authorization Server to start"

./simple_kafka_config.sh | tee /tmp/strimzi.properties

# add Strimzi kafka-oauth-* jars and their dependencies to classpath
export CLASSPATH="/opt/kafka/libs/strimzi/*:$CLASSPATH"

exec /opt/kafka/bin/kafka-server-start.sh /tmp/strimzi.properties