
This project builds and runs Keycloak docker container with enabled https on port 8443.

Certificates, key stores, and trust stores are pre-generated in 'config' directory.

They were generated using the following commands.

#### Create server certificate for Keycloak

    keytool -keystore keycloak.server.keystore.p12 -storetype pkcs12 -keyalg RSA -alias keycloak -validity 366 -genkey -storepass changeit -keypass changeit -dname CN=keycloak -ext SAN=DNS:keycloak


#### Create own CA:

    openssl req -new -x509 -keyout ca-key -out ca-cert -days 3650 -subj "/CN=strimzi.io" -passout pass:changeit


#### Create client truststore:

    keytool -keystore keycloak.client.truststore.p12 -storetype pkcs12 -alias KeycloakCARoot -storepass changeit -keypass changeit -import -file ca-cert -noprompt


#### Sign server certificate (export, sign, add signed to keystore):

    keytool -keystore keycloak.server.keystore.p12 -storetype pkcs12 -alias keycloak -storepass changeit -keypass changeit -certreq -file cert-file

    openssl x509 -req -CA ca-cert -CAkey ca-key -in cert-file -out cert-signed -days 366 -CAcreateserial -passin pass:changeit

    keytool -keystore keycloak.server.keystore.p12 -alias CARoot -storepass changeit -keypass changeit -import -file ca-cert -noprompt
    keytool -keystore keycloak.server.keystore.p12 -alias keycloak -storepass changeit -keypass changeit -import -file cert-signed -noprompt
