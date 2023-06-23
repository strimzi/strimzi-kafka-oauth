#!/bin/sh

docker-compose -f compose.yml -f spring/compose.yml up -d
for i in {1..10}
do
  sleep 1
  RESULT=$(docker logs spring | grep "Started SimpleAuthorizationServerApplication")
  if [ "$RESULT" != "" ]; then
     docker-compose -f compose.yml -f spring/compose.yml down
     exit 0
  fi
done

echo "Failed to start Spring example"
docker logs spring
exit 1
