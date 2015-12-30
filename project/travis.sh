#!/bin/bash
set -ev

TRAVIS_SCALA_VERSION="$1"
shift
TRAVIS_PULL_REQUEST="$1"
shift
TRAVIS_BRANCH="$1"
shift
PUBLISH="$1"
shift


function isNotPr() {
  [ "$TRAVIS_PULL_REQUEST" = "false" ]
}

function publish() {
  [ "$PUBLISH" = 1 ]
}

function isMaster() {
  [ "$TRAVIS_BRANCH" = "master" ]
}

function isMasterOrDevelop() {
  [ "$TRAVIS_BRANCH" = "master" -o "$TRAVIS_BRANCH" = "develop" ]
}

# web sub-project doesn't compile in 2.10 (no scalajs-react)
if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.10"; then
  IS_210=1
  SBT_COMMANDS="cli/compile"
else
  IS_210=0
  SBT_COMMANDS="compile"
fi

# Required for ~/.ivy2/local repo tests
~/sbt coreJVM/publish-local

SBT_COMMANDS="$SBT_COMMANDS test"

# TODO Add coverage once https://github.com/scoverage/sbt-scoverage/issues/111 is fixed

PUSH_GHPAGES=0
if isNotPr && publish && isMaster; then
  SBT_COMMANDS="$SBT_COMMANDS coreJVM/publish coreJS/publish files/publish"
  if [ "$IS_210" = 1 ]; then
    SBT_COMMANDS="$SBT_COMMANDS plugin/publish"
  else
    SBT_COMMANDS="$SBT_COMMANDS cli/publish"
  fi
fi

if isNotPr && publish && isMasterOrDevelop; then
  if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.11"; then
    PUSH_GHPAGES=1
  fi
fi

~/sbt ++${TRAVIS_SCALA_VERSION} $SBT_COMMANDS

[ "$PUSH_GHPAGES" = 0 ] || "$(dirname "$0")/push-gh-pages.sh" "$TRAVIS_SCALA_VERSION"
