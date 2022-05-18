#!/bin/sh

set -e

PASSWORD=changeit

echo
echo "#### Prepare mockoauth.server.keystore.p12"
echo

rm -f mockoauth.server.keystore.p12

echo "#### Create server certificate for Mock OAuth Server"
keytool -keystore mockoauth.server.keystore.p12 -storetype pkcs12 -keyalg RSA -alias mockoauth -validity 3650 -genkey -storepass $PASSWORD -keypass $PASSWORD -dname CN=mockoauth -ext SAN=DNS:mockoauth

echo "#### Sign server certificate with ca.crt (export, sign, add signed to keystore)"
keytool -keystore mockoauth.server.keystore.p12 -storetype pkcs12 -alias mockoauth -storepass $PASSWORD -keypass $PASSWORD -certreq -file cert-file
openssl x509 -req -CA ca.crt -CAkey ca.key -in cert-file -out cert-signed -days 3650 -CAcreateserial -passin pass:$PASSWORD
keytool -keystore mockoauth.server.keystore.p12 -alias CARoot -storepass $PASSWORD -keypass $PASSWORD -import -file ca.crt -noprompt
keytool -keystore mockoauth.server.keystore.p12 -alias mockoauth -storepass $PASSWORD -keypass $PASSWORD -import -file cert-signed -noprompt

echo
echo "#### Prepare mockoauth.server.keystore_2.p12"
echo

rm -f mockoauth.server.keystore_2.p12

echo "#### Create a different CA - ca_2.crt"
openssl genrsa -out ca_2.key 4096
# create CA certificate
openssl req -x509 -new -nodes -sha256 -days 3650 -subj "/CN=strimzi.io" -key ca_2.key -out ca_2.crt

echo "#### Create server certificate for Mock OAuth Server"
keytool -keystore mockoauth.server.keystore_2.p12 -storetype pkcs12 -keyalg RSA -alias mockoauth -validity 3650 -genkey -storepass $PASSWORD -keypass $PASSWORD -dname CN=mockoauth -ext SAN=DNS:mockoauth

echo "#### Sign server certificate with ca_2.crt (export, sign, add signed to keystore)"
keytool -keystore mockoauth.server.keystore_2.p12 -storetype pkcs12 -alias mockoauth -storepass $PASSWORD -keypass $PASSWORD -certreq -file cert-file
openssl x509 -req -CA ca_2.crt -CAkey ca_2.key -in cert-file -out cert-signed -days 3650 -CAcreateserial -passin pass:$PASSWORD
keytool -keystore mockoauth.server.keystore_2.p12 -alias CARoot -storepass $PASSWORD -keypass $PASSWORD -import -file ca_2.crt -noprompt
keytool -keystore mockoauth.server.keystore_2.p12 -alias mockoauth -storepass $PASSWORD -keypass $PASSWORD -import -file cert-signed -noprompt