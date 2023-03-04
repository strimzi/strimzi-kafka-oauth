#!/usr/bin/env bash
set -e

#
# If current Java version is 8 the script will perform a full run including Spotbugs checks, documentation build,
#   and multiple testsuite runs using different versions of Kafka images.
#
# Set environment variable MAIN_BUILD=TRUE to force a full run when current Java version is not 8.
#

clearDockerEnv() {
  docker rm -f kafka zookeeper keycloak keycloak-import hydra hydra-import hydra-jwt hydra-jwt-import || true
  DOCKER_TEST_NETWORKS=$(docker network ls | grep test | awk '{print $1}')
  [ "$DOCKER_TEST_NETWORKS" != "" ] && docker network rm $DOCKER_TEST_NETWORKS
}

exitIfError() {
  [ "$EXIT" != "0" ] && exit $EXIT
}

arch=$(uname -m)

# The first segment of the version number is '1' for releases < 9; then '9', '10', '11', ...
JAVA_MAJOR_VERSION=$(java -version 2>&1 | sed -E -n 's/.* version "([0-9]*).*$/\1/p')
if [ ${JAVA_MAJOR_VERSION} -gt 1 ] ; then
  export JAVA_VERSION=${JAVA_MAJOR_VERSION}
fi

if [ "$arch" == 'x86_64' ] ; then
  # some parts of the workflow should be done only once on the main build which is currently Java 11
  export MAIN_BUILD="TRUE"
fi

export PULL_REQUEST=${PULL_REQUEST:-true}
export BRANCH=${BRANCH:-main}
export TAG=${TAG:-latest}

if [ "${MAIN_BUILD}" == "TRUE" ] ; then
  mvn -e -V -B install
else
  mvn -e -V -B -Dmaven.javadoc.skip=true install
fi

mvn spotbugs:check

# Also test examples build on different architectures (exclude ppc64le until fixed)
if [ "$arch" != 'ppc64le' ]; then
  mvn clean install -f examples/docker
fi

# Run testsuite if this is a main build
if [ "${MAIN_BUILD}" == "TRUE" ] ; then

  if [ "$arch" == 's390x' ]; then
    # Build s390x compatible hydra image
    export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/lib/s390x-linux-gnu/jni
    docker build --target hydra-import -t strimzi-oauth-testsuite/hydra-import:latest -f ./testsuite/docker/hydra-import/Dockerfile.s390x .
    git clone -b 19.0.3 https://github.com/keycloak/keycloak-containers.git
    cd keycloak-containers/server/
    docker build -t quay.io/keycloak/keycloak:19.0.3-legacy .
    cd ../../ && rm -rf keycloak-containers
    docker build --target oryd-hydra -t oryd/hydra:v1.8.5 -f ./testsuite/docker/hydra-import/Dockerfile.s390x .
    mvn test-compile spotbugs:check -e -V -B -f testsuite
    set +e
    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pcustom -Dkafka.docker.image=quay.io/strimzi/kafka:0.31.1-kafka-3.2.3
    EXIT=$?
    exitIfError
    set -e
  else
    mvn test-compile spotbugs:check -e -V -B -f testsuite

    set +e

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_3_2
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_2_3
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_1_2
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_0_0
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-2_8_1
    EXIT=$?
    exitIfError

    set -e
  fi

  # Test example image build for keycloak-ssl example
  cd examples/docker
  docker-compose -f compose.yml -f keycloak/compose-ssl.yml build
  cd ../..
fi

# Push only releases
if [ "$PULL_REQUEST" != "false" ] ; then
    echo "Building Pull Request - nothing to push"
elif [ "$TAG" = "latest" ] && [ "$BRANCH" != "main" ]; then
    echo "Not in main branch and not in release tag - nothing to push"
else
    if [ "${MAIN_BUILD}" = "TRUE" ] ; then
        echo "Pushing JARs"
        ./.travis/push-to-nexus.sh
    fi
fi
