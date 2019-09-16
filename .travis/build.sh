#!/usr/bin/env bash
set -e

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

mvn -e -V -B install
mvn spotbugs:check

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
