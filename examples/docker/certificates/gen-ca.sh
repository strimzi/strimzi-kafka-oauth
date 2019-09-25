#!/bin/sh
set -e

# create CA key
openssl genrsa -out ca.key 4096

# create CA certificate
openssl req -x509 -new -nodes -sha256 -days 3650 -subj "/CN=strimzi.io" -key ca.key -out ca.crt


PASSWORD=changeit

# create p12 truststore
# can't use openssl, the resulting .p12 truststore not usable by java
#openssl pkcs12 -export -nokeys -in ca.crt -out ca-truststore.p12 -passout pass:$PASSWORD

keytool -keystore ca-truststore.p12 -storetype pkcs12 -alias ca -storepass $PASSWORD -keypass $PASSWORD -import -file ca.crt -noprompt

# copy the certificate to client dirs
cp ca.crt ../hydra-import/
cp ca-truststore.p12 ../kafka-oauth-strimzi/kafka/config/
cp ca-truststore.p12 ../keycloak-import/config/