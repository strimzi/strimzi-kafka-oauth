#!/usr/bin/env bash

export GPG_TTY=$(tty)

openssl aes-256-cbc -K $encrypted_a40d2cfb3073_key -iv $encrypted_a40d2cfb3073_iv -in .travis/signing.gpg.enc -out signing.gpg -d
gpg --batch --import signing.gpg

GPG_EXECUTABLE=gpg mvn -B -DskipTests -s ./.travis/settings.xml  -pl '!examples/producer','!examples/consumer' -P ossrh clean verify deploy

rm -rf signing.gpg
gpg --delete-keys
gpg --delete-secret-keys