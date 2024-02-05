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

cd js
rm pnpm-lock.yaml
head -n -2 package.json > tmp.txt
echo '  },
  "resolutions": {
    "rollup": "npm:@rollup/wasm-node"
  },
  "overrides": {
    "rollup": "npm:@rollup/wasm-node"
  }
}' >> tmp.txt
mv tmp.txt package.json
pnpm install --no-frozen-lockfile
cd ..

mvn -e -pl quarkus/deployment,quarkus/dist -am -DskipTests clean install
cd quarkus/container
cp ../dist/target/keycloak-*.tar.gz .
docker build --build-arg KEYCLOAK_DIST=$(ls keycloak-*.tar.gz) . -t quay.io/keycloak/keycloak:23.0.5
cd ../../../.. && rm -rf target/keycloak
