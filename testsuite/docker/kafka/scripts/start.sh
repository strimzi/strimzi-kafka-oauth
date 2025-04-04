#!/bin/bash
set -e

source functions.sh

URI=${KEYCLOAK_URI}
if [ "" == "${URI}" ]; then
    URI="http://${KEYCLOAK_HOST:-keycloak}:8080/admin"
fi

wait_for_url $URI "Waiting for Keycloak to start"

./simple_kafka_config.sh $1 | tee /tmp/strimzi.properties

echo "Config created"

KAFKA_DEBUG_PASSED=$KAFKA_DEBUG
unset KAFKA_DEBUG

# add extra jars to classpath
export CLASSPATH="/opt/kafka/libs/strimzi/*:$CLASSPATH"
echo "CLASSPATH=$CLASSPATH"


KAFKA_CLUSTER_ID="$(/opt/kafka/bin/kafka-storage.sh random-uuid)"
/opt/kafka/bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c /tmp/strimzi.properties \
    --add-scram 'SCRAM-SHA-512=[name=admin,password=admin-secret]' \
    --add-scram 'SCRAM-SHA-512=[name=alice,password=alice-secret]'
echo "Initialised kafka storage for KRaft and added user secrets for SCRAM"


export KAFKA_DEBUG=$KAFKA_DEBUG_PASSED


# set log dir to writable directory
if [ "$LOG_DIR" == "" ]; then
  export LOG_DIR=/tmp/logs
fi

# set log4j properties file to custom one
if [ "$KAFKA_LOG4J_OPTS" == "" ]; then
  export KAFKA_LOG4J_OPTS="-Dlog4j.configuration=file:/opt/kafka/config/strimzi/log4j.properties"
fi
echo "KAFKA_LOG4J_OPTS=$KAFKA_LOG4J_OPTS"

# Prometheus JMX agent config
if [ "$PROMETHEUS_AGENT_CONFIG" == "" ]; then

  if [ "$PROMETHEUS_AGENT_VERSION" == "" ]; then
    PROMETHEUS_AGENT_VERSION=$(ls /opt/kafka/libs/strimzi/jmx_prometheus* | sed -E -n 's/.*([0-9]+\.[0-9]+\.[0-9]+).*$/\1/p')
  fi

  export PROMETHEUS_AGENT_CONFIG="-javaagent:/opt/kafka/libs/strimzi/jmx_prometheus_javaagent-$PROMETHEUS_AGENT_VERSION.jar=9404:/opt/kafka/config/strimzi/metrics-config.yml"
fi
echo "PROMETHEUS_AGENT_CONFIG=$PROMETHEUS_AGENT_CONFIG"

export KAFKA_OPTS="$PROMETHEUS_AGENT_CONFIG $KAFKA_OPTS"
echo "KAFKA_OPTS=$KAFKA_OPTS"

exec /opt/kafka/bin/kafka-server-start.sh /tmp/strimzi.properties
