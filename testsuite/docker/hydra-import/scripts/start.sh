#!/bin/bash
set -e

wait_for_url() {
    URL=$1
    MSG=$2

    if [[ $URL == https* ]]; then
        CMD="curl -k -sL -o /dev/null -w %{http_code} $URL"
    else
        CMD="curl -sL -o /dev/null -w %{http_code} $URL"
    fi

    until [ "200" == "`$CMD`" ]
    do
        echo "$MSG ($URL)"
        sleep 2
    done
}

# add crt to globally trusted
cat < /hydra/certs/ca.crt >> /etc/ssl/certs/ca-certificates.crt

URI=${HYDRA_URI}
if [ "" == "${URI}" ]; then
    URI="http://${HYDRA_HOST:-hydra}:{SERVE_ADMIN_PORT:-4445}/clients"
fi

wait_for_url $URI "Waiting for Hydra admin REST to start"

echo "Creating kafka-broker"
hydra clients create \
      --id kafka-broker \
      --secret kafka-broker-secret \
      --grant-types refresh_token,client_credentials \
      --response-types token,id_token \
      --scope openid,offline

echo "Creating kafka-producer-client"
hydra clients create \
      --id kafka-producer-client \
      --secret kafka-producer-client-secret \
      --grant-types refresh_token,client_credentials \
      --response-types token,id_token \
      --scope openid,offline

echo "Creating kafka-consumer-client"
hydra clients create \
      --id kafka-consumer-client \
      --secret kafka-consumer-client-secret \
      --grant-types refresh_token,client_credentials \
      --response-types token,id_token \
      --scope openid,offline


