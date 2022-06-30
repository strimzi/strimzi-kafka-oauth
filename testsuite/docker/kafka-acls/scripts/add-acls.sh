#!/bin/bash
set -e

# Add user 'admin'
#/opt/kafka/bin/kafka-configs.sh --bootstrap-server kafka:9100 --command-config admin.properties --alter --add-config 'SCRAM-SHA-512=[password=admin-secret]' --entity-type users --entity-name admin
# Add user 'alice'
#/opt/kafka/bin/kafka-configs.sh --bootstrap-server kafka:9100 --command-config admin.properties --alter --add-config 'SCRAM-SHA-512=[password=alice-secret]' --entity-type users --entity-name alice

# Add ACL for user 'bobby'
/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config admin.properties --add --allow-principal User:bobby --operation Describe --operation Create --operation Write --topic KeycloakAuthorizationTest-multiSaslTest-plain
/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config admin.properties --add --allow-principal User:bobby --operation IdempotentWrite --cluster kafka-cluster
# Add ACL for user 'alice'
/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config admin.properties --add --allow-principal User:alice --operation Describe --operation Create --operation Write --topic KeycloakAuthorizationTest-multiSaslTest-scram
/opt/kafka/bin/kafka-acls.sh --bootstrap-server kafka:9100 --command-config admin.properties --add --allow-principal User:alice --operation IdempotentWrite --cluster kafka-cluster
