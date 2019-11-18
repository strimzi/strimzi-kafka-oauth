# Strimzi Kafka OAuth release process

This document describes the steps needed for release of Strimti Kafka OAuth library.

## Create new release branch

The release branch should be named `release-M.N.x` where `M.N` is replaced with the major and minor versions of the new release.
For example:

```
git checkout -b release-0.2.x
```

## Set new version in main Maven project

Update the version for the new Maven project:

```
mvn versions:set -DnewVersion=X.Y.Z
```

Where `X.Y.Z` is the new version which is going to be released.

## Update dependencies in examples and tests

Update the version in dependencies in:
* `examples/docker/pom.xml`
* `testsuite/pom.xml`

These are independent Maven projects, so the dependency version needs ot be bumped manually.

## Tag the new release

Tag the new release as `X.Y.Z` or `X.Y.Z-rc1`.
Have travis run the tests and build it.

## Release JARs to Maven Central

After the GA release (not for RCs), release the JARs in Sonatype.