#!/bin/bash
set -e

wait_for_url() {
    URL=$1
    MSG=$2

    if [[ $URL == https* ]]; then
        CMD="curl --cacert ca-cert -sL -o /dev/null -w %{http_code} $URL"
    else
        CMD="curl -sL -o /dev/null -w %{http_code} $URL"
    fi

    until [ "200" == "`$CMD`" ]
    do
        echo "$MSG ($URL)"
        sleep 2
    done
}

URI=${KEYCLOAK_URI}
if [ "" == "${URI}" ]; then
    URI="http://${KEYCLOAK_IP:-keycloak}:8080/auth"
fi

wait_for_url $URI "Waiting for Keycloak to start"

PATH=$PATH:/opt/jboss/keycloak/bin

FILES=realms/*.json

if [ "" != "$TRUSTSTORE" ]; then
    kcadm.sh config truststore --trustpass "$TRUSTSTORE_PASSWORD" "$TRUSTSTORE"
fi

kcadm.sh config credentials --server $URI --realm master --user admin --password admin

for FILE in $FILES
do
  echo "Importing realm file: $FILE"
  kcadm.sh create realms -f $FILE
done

rm -rf ~/.keycloak