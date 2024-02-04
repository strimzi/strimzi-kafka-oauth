#!/usr/bin/env bash
set -e

clearDockerEnv() {
  docker rm -f kafka zookeeper keycloak keycloak-import hydra hydra-import hydra-jwt hydra-jwt-import kerberos || true
  DOCKER_TEST_NETWORKS=$(docker network ls | grep test | awk '{print $1}')
  [ "$DOCKER_TEST_NETWORKS" != "" ] && docker network rm $DOCKER_TEST_NETWORKS
}

exitIfError() {
  [ "$EXIT" != "0" ] && exit $EXIT
}

arch=$(uname -m)
echo "Architecture: $arch"

if [ "$arch" == 's390x' ]; then
   set -x
   # install maven that works with java17
   wget https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
   mkdir ~/opt
   tar xzf apache-maven-3.9.6-bin.tar.gz -C ~/opt/
   mv apache-maven-3.8.5 ~/opt/maven
   export M2_HOME=$HOME/opt/maven
   export PATH=${M2_HOME}/bin:${PATH}
   mvn -v
   set +x
fi

# The first segment of the version number is '1' for releases < 9; then '9', '10', '11', ...
JAVA_MAJOR_VERSION=$(java -version 2>&1 | sed -E -n 's/.* version "([0-9]*).*$/\1/p')
echo "JAVA_MAJOR_VERSION: $JAVA_MAJOR_VERSION"

if [ "$SKIP_DISABLED" == "" ]; then
  SKIP_DISABLED="true"
fi
echo "SKIP_DISABLED: $SKIP_DISABLED"

export PULL_REQUEST=${PULL_REQUEST:-true}
export BRANCH=${BRANCH:-main}
export TAG=${TAG:-latest}

if [ "$arch" != 'ppc64le' ] && [ "$arch" != 's390x' ]; then
  export MAVEN_EXTRA_ARGS=--no-transfer-progress
fi
echo "MAVEN_EXTRA_ARGS: $MAVEN_EXTRA_ARGS"

mvn -e -V -B clean install $MAVEN_EXTRA_ARGS
mvn spotbugs:check

# Also test examples build on different architectures (exclude ppc64le until fixed)
if [ "$arch" != 'ppc64le' ]; then
  mvn clean install -f examples/docker $MAVEN_EXTRA_ARGS
  cd examples/docker
  set +e
  ./spring/test-spring.sh
  EXIT=$?
  cd ../..
  exitIfError
  set -e
fi

# Run testsuite
if [ "$arch" != 'ppc64le' ] && [ "$arch" != 's390x' ]; then
  mvn test-compile spotbugs:check -e -V -B -f testsuite $MAVEN_EXTRA_ARGS

  set +e

  clearDockerEnv
  mvn -e -V -B clean install -f testsuite -Pkafka-3_6_1 $MAVEN_EXTRA_ARGS
  EXIT=$?
  exitIfError

  # Excluded by default to not exceed Travis job timeout
  if [ "$SKIP_DISABLED" == "false" ]; then

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_5_2 $MAVEN_EXTRA_ARGS
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_4_0 $MAVEN_EXTRA_ARGS
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_3_2 $MAVEN_EXTRA_ARGS
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_2_3 $MAVEN_EXTRA_ARGS -DfailIfNoTests=false -Dtest=\!KeycloakKRaftAuthorizationTests
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_1_2 $MAVEN_EXTRA_ARGS -DfailIfNoTests=false -Dtest=\!KeycloakKRaftAuthorizationTests,\!KeycloakZKAuthorizationTests
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-3_0_0 $MAVEN_EXTRA_ARGS -DfailIfNoTests=false -Dtest=\!KeycloakKRaftAuthorizationTests,\!KeycloakZKAuthorizationTests
    EXIT=$?
    exitIfError

    clearDockerEnv
    mvn -e -V -B clean install -f testsuite -Pkafka-2_8_1 $MAVEN_EXTRA_ARGS -DfailIfNoTests=false -Dtest=\!KeycloakKRaftAuthorizationTests,\!KeycloakZKAuthorizationTests
    EXIT=$?
    exitIfError
  fi

  set -e
fi
