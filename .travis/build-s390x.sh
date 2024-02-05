#!/usr/bin/env bash
set -e
set -x

export PULL_REQUEST=${PULL_REQUEST:-true}
export BRANCH=${BRANCH:-main}
export TAG=${TAG:-latest}

export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/lib/s390x-linux-gnu/jni

cd target
git clone -b 23.0.5 https://github.com/keycloak/keycloak.git
cd keycloak
mvn -e -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install
cd quarkus/container
cp ../dist/target/keycloak-*.tar.gz .
docker build --build-arg KEYCLOAK_DIST=$(ls keycloak-*.tar.gz) . -t quay.io/keycloak/keycloak:23.0.5
cd ../../../.. && rm -rf target/keycloak
