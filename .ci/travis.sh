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
~/sbt ++2.11.8 coreJVM/publishLocal http-server/publishLocal

# Required for HTTP authentication tests
./coursier launch \
  io.get-coursier:http-server-java7_2.11:1.0.0-SNAPSHOT \
  -r http://dl.bintray.com/scalaz/releases \
  -- \
    -d tests/jvm/src/test/resources/test-repo/http/abc.com \
    -u user -P pass -r realm \
    -v &

# TODO Add coverage once https://github.com/scoverage/sbt-scoverage/issues/111 is fixed

SBT_COMMANDS="compile test it:test"

RUN_SHADING_TESTS=1

if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.10"; then
  SBT_COMMANDS="$SBT_COMMANDS publishLocal" # to make the scripted tests happy
  SBT_COMMANDS="$SBT_COMMANDS plugin/scripted"

  if [ "$RUN_SHADING_TESTS" = 1 ]; then
    # for the shading scripted test
    sudo cp coursier /usr/local/bin/

    JARJAR_VERSION=1.0.1-coursier-SNAPSHOT

    if [ ! -d "$HOME/.m2/repository/org/anarres/jarjar/jarjar-core/$JARJAR_VERSION" ]; then
      git clone https://github.com/alexarchambault/jarjar.git
      cd jarjar
      if ! grep -q "^version=$JARJAR_VERSION\$" gradle.properties; then
        echo "Expected jarjar version not found" 1>&2
        exit 1
      fi
      git checkout 249c8dbb970f8
      ./gradlew :jarjar-core:install
      cd ..
      rm -rf jarjar
    fi

    SBT_COMMANDS="$SBT_COMMANDS plugin/publishLocal sbt-shading/scripted"
  fi
fi

SBT_COMMANDS="$SBT_COMMANDS tut coreJVM/mimaReportBinaryIssues cache/mimaReportBinaryIssues"

~/sbt ++${TRAVIS_SCALA_VERSION} $SBT_COMMANDS

.ci/java-6-test.sh

if isNotPr && publish && isMaster; then
  ~/sbt ++${TRAVIS_SCALA_VERSION} publish
fi

PUSH_GHPAGES=0
if isNotPr && publish && isMasterOrDevelop; then
  if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.11"; then
    PUSH_GHPAGES=1
  fi
fi

# [ "$PUSH_GHPAGES" = 0 ] || "$(dirname "$0")/push-gh-pages.sh" "$TRAVIS_SCALA_VERSION"
