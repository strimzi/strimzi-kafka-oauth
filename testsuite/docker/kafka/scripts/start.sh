#!/bin/bash
set -e

# Ensure strimzi libs directory exists and prepend to classpath.
# This makes SNAPSHOT OAuth JARs take precedence over any bundled versions.
mkdir -p /opt/kafka/libs/strimzi
export CLASSPATH="/opt/kafka/libs/strimzi/*:${CLASSPATH:-}"

# Use custom log4j config if available (enables TRACE for io.strimzi)
if [ -f /opt/kafka/config/strimzi/log4j.properties ]; then
    export KAFKA_LOG4J_OPTS="-Dlog4j.configuration=file:/opt/kafka/config/strimzi/log4j.properties"
fi

# Set up Prometheus JMX agent if metrics config is provided
if [ -n "$OAUTH_METRICS_CONFIG" ]; then
    PROMETHEUS_AGENT_VERSION=$(ls /opt/kafka/libs/strimzi/jmx_prometheus* 2>/dev/null \
        | sed -E -n 's/.*([0-9]+\.[0-9]+\.[0-9]+).*$/\1/p' | head -1)
    if [ -n "$PROMETHEUS_AGENT_VERSION" ]; then
        export KAFKA_OPTS="-javaagent:/opt/kafka/libs/strimzi/jmx_prometheus_javaagent-${PROMETHEUS_AGENT_VERSION}.jar=9404:${OAUTH_METRICS_CONFIG} ${KAFKA_OPTS:-}"
    fi
fi

# Wait for StrimziKafkaContainer.containerIsStarting() to write server.properties
while [ ! -f /opt/kafka/config/kraft/server.properties ]; do sleep 0.1; done

# Build SCRAM user args if configured
# OAUTH_SCRAM_USERS format: "SCRAM-SHA-512=[name=admin,password=admin-secret];SCRAM-SHA-512=[name=alice,password=alice-secret]"
SCRAM_ARGS=""
if [ -n "$OAUTH_SCRAM_USERS" ]; then
    IFS=';' read -ra USERS <<< "$OAUTH_SCRAM_USERS"
    for user in "${USERS[@]}"; do
        SCRAM_ARGS="${SCRAM_ARGS} --add-scram ${user}"
    done
fi

# Format storage and start Kafka
/opt/kafka/bin/kafka-storage.sh format \
    -t "$(/opt/kafka/bin/kafka-storage.sh random-uuid)" \
    -c /opt/kafka/config/kraft/server.properties $SCRAM_ARGS

exec /opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/kraft/server.properties
