#!/usr/bin/env bash

openssl aes-256-cbc -K $encrypted_9bb2e0bcb75b_key -iv $encrypted_9bb2e0bcb75b_iv -in .travis/signing.gpg.enc -out signing.gpg -d
gpg --import signing.gpg

GPG_EXECUTABLE=gpg mvn -B -DskipTests -s ./.travis/settings.xml  -pl '!examples/producer','!examples/consumer' -P ossrh clean package gpg:sign deploy

rm -rf signing.gpg
gpg --delete-keys
gpg --delete-secret-keys