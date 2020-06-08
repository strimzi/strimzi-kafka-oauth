#!/bin/bash
set -e

./simple_kafka_config.sh | tee /tmp/strimzi.properties

# add Strimzi kafka-oauth-* jars and their dependencies to classpath
export CLASSPATH="/opt/kafka/libs/strimzi/*:$CLASSPATH"

exec /opt/kafka/bin/kafka-server-start.sh /tmp/strimzi.properties