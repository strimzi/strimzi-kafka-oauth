#!/usr/bin/env bash
set -e
set -x

export PULL_REQUEST=${PULL_REQUEST:-true}
export BRANCH=${BRANCH:-main}
export TAG=${TAG:-latest}

export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/lib/s390x-linux-gnu/jni

cd target
git clone -b 23.0.5 https://github.com/keycloak/keycloak.git
cd keycloak/quarkus/container
docker build . -t quay.io/keycloak/keycloak:23.0.5

cd ../../../.. && rm -rf target/keycloak

docker build --target hydra-import -t strimzi-oauth-testsuite/hydra-import:latest -f ./testsuite/docker/hydra-import/Dockerfile.s390x .

docker build --target oryd-hydra -t oryd/hydra:v1.8.5 -f ./testsuite/docker/hydra-import/Dockerfile.s390x .

mvn -q test-compile spotbugs:check -e -V -B -f testsuite
mvn -e -V -B clean install -f testsuite -Pkafka-3_6_1
