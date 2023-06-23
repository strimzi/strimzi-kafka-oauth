#!/bin/sh
set -x

docker rm -f spring
docker run -d --name spring strimzi/example-spring
for i in 1 2 3 4 5
do
  sleep 1
  RESULT=$(docker logs spring | grep "Started SimpleAuthorizationServerApplication")
  if [[ "$RESULT" != "" ]]; then
     docker rm -f spring
     exit 0
  fi
done

echo "Failed to start Spring example"
docker logs spring
exit 1
