#!/bin/bash
set -e

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

PATH=$PATH:/opt/jboss/keycloak/bin

FILES=realms/*.json

kcadm.sh config credentials --server http://${KEYCLOAK_IP:-keycloak}:8080/auth --realm master --user admin --password admin

for FILE in $FILES
do
  echo "Importing realm file: $FILE"
  kcadm.sh create realms -f $FILE
done

rm -rf ~/.keycloak