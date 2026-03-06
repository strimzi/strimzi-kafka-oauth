include ./Makefile.os
include ./Makefile.maven

RELEASE_VERSION ?= latest
PROJECT_NAME ?= kafka-kubernetes-config-provider

.PHONY: all
all: java_install spotbugs

.PHONY: release
release: release_maven release_package

.PHONY: release_maven
release_maven:
	echo "Update pom versions to $(RELEASE_VERSION)"
	mvn $(MVN_ARGS) versions:set -DnewVersion=$(shell echo $(RELEASE_VERSION) | tr a-z A-Z)
	mvn $(MVN_ARGS) versions:commit

.PHONY: release_package
release_package: java_package

.PHONY: clean
clean: java_clean
