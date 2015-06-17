#!/bin/bash
set -ev

TRAVIS_SCALA_VERSION="$1"
shift
TRAVIS_PULL_REQUEST="$1"
shift
TRAVIS_BRANCH="$1"
shift
JDK7_HOME="$1"
shift


function isNotPr() {
  [ "$TRAVIS_PULL_REQUEST" = "false" ]
}

function isJdk7() {
  [ "$JAVA_HOME" = "$JDK7_HOME" ]
}

function isMaster() {
  [ "$TRAVIS_BRANCH" = "master" ]
}

# web sub-project doesn't compile in 2.10 (no scalajs-react)
if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.10"; then
  SBT_COMMANDS="cli/compile"
else
  SBT_COMMANDS="compile"
fi

SBT_COMMANDS="$SBT_COMMANDS core-jvm/test core-js/test"

if isNotPr && isJdk7 && isMaster; then
  SBT_COMMANDS="$SBT_COMMANDS core-jvm/publish core-js/publish cli/publish"
fi

sbt ++${TRAVIS_SCALA_VERSION} $SBT_COMMANDS
