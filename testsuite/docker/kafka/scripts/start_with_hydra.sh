#!/bin/bash
set -e

source functions.sh

URI=${HYDRA_URI}
if [ "" == "${URI}" ]; then
    URI="https://${HYDRA_HOST:-hydra}:4445/clients"
fi

wait_for_url $URI "Waiting for Hydra admin REST to start"

wait_for_url $URI/kafka-broker "Waiting for kafka-broker client to be available"

./simple_kafka_config.sh | tee /tmp/strimzi.properties

# set log dir to writable directory
if [ "$LOG_DIR" == "" ]; then
  export LOG_DIR=/tmp/logs
fi

# set log4j properties file to custom one
export KAFKA_LOG4J_OPTS="-Dlog4j.configuration=file:/opt/kafka/config/strimzi/log4j.properties"

# add extra jars to classpath
export CLASSPATH="/opt/kafka/libs/strimzi/*:$CLASSPATH"

exec /opt/kafka/bin/kafka-server-start.sh /tmp/strimzi.properties