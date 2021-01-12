#!/bin/bash
set -e

source functions.sh

URI=${KEYCLOAK_URI}
if [ "" == "${URI}" ]; then
    URI="http://${KEYCLOAK_HOST:-keycloak}:8080/auth"
fi

wait_for_url $URI "Waiting for Keycloak to start"

wait_for_url "$URI/realms/${REALM:-demo}" "Waiting for realm '${REALM}' to be available"

./simple_kafka_config.sh | tee /tmp/strimzi.properties


# Add 'admin' user
/opt/kafka/bin/kafka-configs.sh --zookeeper zookeeper:2181 --alter --add-config 'SCRAM-SHA-512=[password=admin-secret]' --entity-type users --entity-name admin

# Add 'alice' user
/opt/kafka/bin/kafka-configs.sh --zookeeper zookeeper:2181 --alter --add-config 'SCRAM-SHA-512=[password=alice-secret]' --entity-type users --entity-name alice


# set log dir to writable directory
if [ "$LOG_DIR" == "" ]; then
  export LOG_DIR=/tmp/logs
fi

# set log4j properties file to custom one
export KAFKA_LOG4J_OPTS="-Dlog4j.configuration=file:/opt/kafka/config/strimzi/log4j.properties"

# add extra jars to classpath
export CLASSPATH="/opt/kafka/libs/strimzi/*:$CLASSPATH"

exec /opt/kafka/bin/kafka-server-start.sh /tmp/strimzi.properties
