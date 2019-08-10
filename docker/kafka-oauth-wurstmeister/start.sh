#!/bin/bash

wait_for_url() {
    URL=$1
    MSG=$2

    until [ "200" = `curl -sL -o /dev/null -w "%{http_code}" $URL` ]
    do
        echo "$MSG ($URL)"
        sleep 2
    done
}

wait_for_url "http://${KEYCLOAK_IP:-keycloak}:8080/auth" "Waiting for Keycloak to start"

wait_for_url "http://${KEYCLOAK_IP:-keycloak}:8080/auth/realms/${REALM:-demo}" "Waiting for realm '${REALM}' to be available"

/usr/bin/start-kafka.sh