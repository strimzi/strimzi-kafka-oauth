#!/bin/sh
set -x

docker version
docker images
docker ps

docker rm -f spring
docker run --rm -t --name spring strimzi/example-spring &
for i in {1..10}
do
  sleep 1
  RESULT=$(docker logs spring | grep "Started SimpleAuthorizationServerApplication")
  if [ "$RESULT" != "" ]; then
     docker rm -f spring
     exit 0
  fi
done

echo "Failed to start Spring example"
docker logs spring
exit 1
