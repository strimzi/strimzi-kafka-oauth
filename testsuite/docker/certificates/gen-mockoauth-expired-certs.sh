#!/bin/sh

set -e

PASSWORD=changeit
PASTTIME='2021-08-01 11:00:00'

echo "#### Create server certificate for Mock OAuth Server that are already expired"
faketime "$PASTTIME" keytool -keystore mockoauth.server.keystore_expired.p12 -storetype pkcs12 -keyalg RSA -alias mockoauth -validity 1 -genkey -storepass $PASSWORD -keypass $PASSWORD -dname CN=mockoauth -ext SAN=DNS:mockoauth

echo "#### Sign server certificate (export, sign, add signed to keystore)"
keytool -keystore mockoauth.server.keystore_expired.p12 -storetype pkcs12 -alias mockoauth -storepass $PASSWORD -keypass $PASSWORD -certreq -file cert-file
faketime "$PASTTIME" openssl x509 -req -CA ca.crt -CAkey ca.key -in cert-file -out cert-signed -days 1 -CAcreateserial -passin pass:$PASSWORD
keytool -keystore mockoauth.server.keystore_expired.p12 -alias CARoot -storepass $PASSWORD -keypass $PASSWORD -import -file ca.crt -noprompt
keytool -keystore mockoauth.server.keystore_expired.p12 -alias mockoauth -storepass $PASSWORD -keypass $PASSWORD -import -file cert-signed -noprompt
