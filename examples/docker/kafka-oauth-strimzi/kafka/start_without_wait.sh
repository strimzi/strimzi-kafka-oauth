#!/bin/bash
set -e

./simple_kafka_config.sh | tee /tmp/strimzi.properties

KAFKA_CLUSTER_ID="$(/opt/kafka/bin/kafka-storage.sh random-uuid)"
/opt/kafka/bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c /tmp/strimzi.properties \
    --add-scram 'SCRAM-SHA-512=[name=admin,password=admin-secret]' \
    --add-scram 'SCRAM-SHA-512=[name=alice,password=alice-secret]'
echo "Initialised kafka storage for KRaft and added user secrets for SCRAM"


# add Strimzi kafka-oauth-* jars and their dependencies to classpath
export CLASSPATH="/opt/kafka/libs/strimzi/*:$CLASSPATH"

exec /opt/kafka/bin/kafka-server-start.sh /tmp/strimzi.properties