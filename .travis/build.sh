#!/usr/bin/env bash
set -e

clearDockerEnv() {
  docker rm -f kafka zookeeper keycloak keycloak-import hydra hydra-import || true
  DOCKER_TEST_NETWORKS=$(docker network ls | grep test | awk '{print $1}')
  [ "$DOCKER_TEST_NETWORKS" != "" ] && docker network rm $DOCKER_TEST_NETWORKS
}

exitIfError() {
  FILE=testsuite/kafka.log
  [ "$EXIT" != "0" ] && test -f "$FILE" && cat $FILE && exit $EXIT
}

# The first segment of the version number is '1' for releases < 9; then '9', '10', '11', ...
JAVA_MAJOR_VERSION=$(java -version 2>&1 | sed -E -n 's/.* version "([0-9]*).*$/\1/p')
if [ ${JAVA_MAJOR_VERSION} -gt 1 ] ; then
  export JAVA_VERSION=${JAVA_MAJOR_VERSION}
fi

if [ ${JAVA_MAJOR_VERSION} -eq 1 ] ; then
  # some parts of the workflow should be done only one on the main build which is currently Java 8
  export MAIN_BUILD="TRUE"
fi

export PULL_REQUEST=${PULL_REQUEST:-true}
export BRANCH=${BRANCH:-master}
export TAG=${TAG:-latest}

if [ ${JAVA_MAJOR_VERSION} -eq 1 ] ; then
  mvn -e -V -B install
else
  mvn -e -V -B -Dmaven.javadoc.skip=true install
fi

mvn spotbugs:check

# Run testsuite with java 8 only
if [ ${JAVA_MAJOR_VERSION} -eq 1 ] ; then

  docker pull oryd/hydra:v1.0.0
  mvn test-compile spotbugs:check -e -V -B -f testsuite

  set +e

  clearDockerEnv
  docker pull strimzi/kafka:latest-kafka-2.6.0
  mvn -e -V -B clean install -f testsuite -Pkafka-2_6_0
  EXIT=$?
  exitIfError

  clearDockerEnv
  docker pull strimzi/kafka:latest-kafka-2.5.1
  mvn -e -V -B test -f testsuite -Pkafka-2_5_1
  EXIT=$?
  exitIfError

  clearDockerEnv
  docker pull strimzi/kafka:latest-kafka-2.4.1
  mvn -e -V -B test -f testsuite -Pkafka-2_4_1
  EXIT=$?
  exitIfError

#  clearDockerEnv
#  docker pull strimzi/kafka:latest-kafka-2.3.0
#  mvn -e -V -B test -f testsuite -Pkafka-2_3_0
#  EXIT=$?
#  exitIfError

  set -e
fi

# Push only releases
if [ "$PULL_REQUEST" != "false" ] ; then
    echo "Building Pull Request - nothing to push"
elif [ "$TAG" = "latest" ] && [ "$BRANCH" != "master" ]; then
    echo "Not in master branch and not in release tag - nothing to push"
else
    if [ "${MAIN_BUILD}" = "TRUE" ] ; then
        echo "Pushing JARs"
        ./.travis/push-to-nexus.sh
    fi
fi
