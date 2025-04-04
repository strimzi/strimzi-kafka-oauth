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

KAFKA_CLUSTER_ID="$(/opt/kafka/bin/kafka-storage.sh random-uuid)"
/opt/kafka/bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c /tmp/strimzi.properties \
    --add-scram 'SCRAM-SHA-512=[name=admin,password=admin-secret]' \
    --add-scram 'SCRAM-SHA-512=[name=alice,password=alice-secret]'
echo "Initialised kafka storage for KRaft and added user secrets for SCRAM"

# add Strimzi kafka-oauth-* jars and their dependencies to classpath
export CLASSPATH="/opt/kafka/libs/strimzi/*:$CLASSPATH"

exec /opt/kafka/bin/kafka-server-start.sh /tmp/strimzi.properties