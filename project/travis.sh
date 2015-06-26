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

function isMasterOrDevelop() {
  [ "$TRAVIS_BRANCH" = "master" -o "$TRAVIS_BRANCH" = "develop" ]
}

# web sub-project doesn't compile in 2.10 (no scalajs-react)
if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.10"; then
  SBT_COMMANDS="cli/compile"
else
  SBT_COMMANDS="compile"
fi

sbt ++2.11.6 core-jvm/publish-local # Required for ~/.ivy2/local repo tests
SBT_COMMANDS="$SBT_COMMANDS core-jvm/test core-js/test"

PUSH_GHPAGES=0
if isNotPr && isJdk7 && isMaster; then
  SBT_COMMANDS="$SBT_COMMANDS core-jvm/publish core-js/publish cli/publish"
fi

if isNotPr && isJdk7 && isMasterOrDevelop; then
  if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.11"; then
    PUSH_GHPAGES=1
  fi
fi

sbt ++${TRAVIS_SCALA_VERSION} $SBT_COMMANDS

[ "$PUSH_GHPAGES" = 0 ] || "$(dirname "$0")/push-gh-pages.sh" "$TRAVIS_SCALA_VERSION"
