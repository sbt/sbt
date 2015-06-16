#!/bin/bash
set -ev

TRAVIS_SCALA_VERSION="$1"
shift
TRAVIS_PULL_REQUEST="$1"
shift
TRAVIS_BRANCH="$1"
shift


function isPr() {
  [ "$TRAVIS_PULL_REQUEST" = "false" ]
}

function isJdk7() {
  [ "$JAVA_HOME" = "$(jdk_switcher home oraclejdk7)" ]
}

function isMaster() {
  [ "$TRAVIS_BRANCH" = "master" ]
}

if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.10"; then
  if isPr && isJdk7 && isMaster; then
    EXTRA_SBT_ARGS="core-jvm/publish core-js/publish cli/publish"
  else
    EXTRA_SBT_ARGS=""
  fi

  sbt ++${TRAVIS_SCALA_VERSION} core-jvm/test core-js/test cli/test $EXTRA_SBT_ARGS
else
  if isPr && isJdk7 && isMaster; then
    EXTRA_SBT_ARGS="publish"
  else
    EXTRA_SBT_ARGS=""
  fi

  sbt ++${TRAVIS_SCALA_VERSION} test $EXTRA_SBT_ARGS
fi
