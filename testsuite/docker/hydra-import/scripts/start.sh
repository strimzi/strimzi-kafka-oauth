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
    URI="https://${HYDRA_HOST:-hydra}:{SERVE_ADMIN_PORT:-4445}/admin/clients"
fi

wait_for_url $URI "Waiting for Hydra admin REST to start"

echo "Creating kafka-broker"
curl -s -X POST $URI -H "Content-Type: application/json" -d '{"client_id": "kafka-broker", "client_secret": "kafka-broker-secret", "grant_types": ["refresh_token","client_credentials"], "response_types": ["token", "id_token"], "scope": "openid,offline"}'

echo "Creating kafka-producer-client"
curl -s -X POST $URI -H "Content-Type: application/json" -d '{"client_id": "kafka-producer-client", "client_secret": "kafka-producer-client-secret", "grant_types": ["refresh_token","client_credentials"], "response_types": ["token", "id_token"], "scope": "openid,offline"}'

echo "Creating kafka-consumer-client"
curl -s -X POST $URI -H "Content-Type: application/json" -d '{"client_id": "kafka-consumer-client", "client_secret": "kafka-consumer-client-secret", "grant_types": ["refresh_token","client_credentials"], "response_types": ["token", "id_token"], "scope": "openid,offline"}'

echo "Hydra import complete"

