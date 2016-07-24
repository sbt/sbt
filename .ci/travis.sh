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

# Required for ~/.ivy2/local repo tests
~/sbt coreJVM/publishLocal simple-web-server/publishLocal

# Required for HTTP authentication tests
./coursier launch \
  io.get-coursier:simple-web-server_2.11:1.0.0-SNAPSHOT \
  -r http://dl.bintray.com/scalaz/releases \
  -- \
    -d tests/jvm/src/test/resources/test-repo/http/abc.com \
    -u user -P pass -r realm \
    -v &

# TODO Add coverage once https://github.com/scoverage/sbt-scoverage/issues/111 is fixed

SBT_COMMANDS="compile test"

if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.10"; then
  SBT_COMMANDS="$SBT_COMMANDS publishLocal" # to make the scripted tests happy
  SBT_COMMANDS="$SBT_COMMANDS plugin/scripted"
fi

SBT_COMMANDS="$SBT_COMMANDS tut coreJVM/mimaReportBinaryIssues cache/mimaReportBinaryIssues"

if isNotPr && publish && isMaster; then
  SBT_COMMANDS="$SBT_COMMANDS publish"
fi

PUSH_GHPAGES=0
if isNotPr && publish && isMasterOrDevelop; then
  if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.11"; then
    PUSH_GHPAGES=1
  fi
fi

~/sbt ++${TRAVIS_SCALA_VERSION} $SBT_COMMANDS

# [ "$PUSH_GHPAGES" = 0 ] || "$(dirname "$0")/push-gh-pages.sh" "$TRAVIS_SCALA_VERSION"
