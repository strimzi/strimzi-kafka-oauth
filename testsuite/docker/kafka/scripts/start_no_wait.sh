#!/bin/bash
set -e

./simple_kafka_config.sh | tee /tmp/strimzi.properties

# set log dir to writable directory
if [ "$LOG_DIR" == "" ]; then
  export LOG_DIR=/tmp/logs
fi

# set log4j properties file to custom one
export KAFKA_LOG4J_OPTS="-Dlog4j.configuration=file:/opt/kafka/config/strimzi/log4j.properties"

# add extra jars to classpath
export CLASSPATH="/opt/kafka/libs/strimzi/*:$CLASSPATH"
export PROMETHEUS_AGENT_VERSION=$(ls /opt/kafka/libs/jmx_prometheus* | sed -E -n 's/.*([0-9]+\.[0-9]+\.[0-9]+).*$/\1/p')
export KAFKA_OPTS=-javaagent:/opt/kafka/libs/jmx_prometheus_javaagent-$PROMETHEUS_AGENT_VERSION.jar=9404:/opt/kafka/config/strimzi/metrics-config.json
exec /opt/kafka/bin/kafka-server-start.sh /tmp/strimzi.properties