#!/usr/bin/env bash
set -evx

SCALA_VERSION="${SCALA_VERSION:-${TRAVIS_SCALA_VERSION:-2.12.4}}"
PULL_REQUEST="${PULL_REQUEST:-${TRAVIS_PULL_REQUEST:-false}}"
BRANCH="${BRANCH:-${TRAVIS_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}}"
PUBLISH="${PUBLISH:-0}"
SCALA_JS="${SCALA_JS:-0}"

VERSION="$(grep -oP '(?<=")[^"]*(?!<")' < version.sbt)"

JARJAR_VERSION="${JARJAR_VERSION:-1.0.1-coursier-SNAPSHOT}"

is210() {
  echo "$SCALA_VERSION" | grep -q "^2\.10"
}

is211() {
  echo "$SCALA_VERSION" | grep -q "^2\.11"
}

is212() {
  echo "$SCALA_VERSION" | grep -q "^2\.12"
}

setupCoursierBinDir() {
  mkdir -p bin
  cp coursier bin/
  export PATH="$(pwd)/bin:$PATH"
}

downloadInstallSbtExtras() {
  mkdir -p bin
  curl -L -o bin/sbt https://github.com/paulp/sbt-extras/raw/9ade5fa54914ca8aded44105bf4b9a60966f3ccd/sbt
  chmod +x bin/sbt
}

launchTestRepo() {
  ./scripts/launch-test-repo.sh "$@"
}

launchProxyRepos() {
  if [ "$(uname)" != "Darwin" ]; then
    ./scripts/launch-proxies.sh
  fi
}

integrationTestsRequirements() {
  # Required for ~/.ivy2/local repo tests
  sbt ++2.11.12 coreJVM/publishLocal ++2.12.4 cli/publishLocal

  # Required for HTTP authentication tests
  launchTestRepo --port 8080 --list-pages

  # Required for missing directory listing tests (no --list-pages)
  launchTestRepo --port 8081
}

isScalaJs() {
  [ "$SCALA_JS" = 1 ]
}

sbtCoursier() {
  [ "$SBT_COURSIER" = 1 ]
}

sbtShading() {
  [ "$SBT_SHADING" = 1 ]
}

runSbtCoursierTests() {
  addPgpKeys
  sbt ++$SCALA_VERSION sbt-plugins/publishLocal
  if [ "$SCALA_VERSION" = "2.10" ]; then
    sbt ++$SCALA_VERSION "sbt-coursier/scripted sbt-coursier/*" "sbt-coursier/scripted sbt-coursier-0.13/*"
  else
    sbt ++$SCALA_VERSION "sbt-coursier/scripted sbt-coursier/simple" # full scripted suite currently taking too long on Travis CI...
  fi
  sbt ++$SCALA_VERSION sbt-pgp-coursier/scripted
}

runSbtShadingTests() {
  sbt ++$SCALA_VERSION coreJVM/publishLocal cacheJVM/publishLocal extra/publishLocal sbt-shared/publishLocal sbt-coursier/publishLocal "sbt-shading/scripted sbt-shading/*"
  if [ "$SCALA_VERSION" = "2.10" ]; then
    sbt ++$SCALA_VERSION "sbt-shading/scripted sbt-shading-0.13/*"
  fi
}

jsCompile() {
  sbt ++$SCALA_VERSION js/compile js/test:compile coreJS/fastOptJS cacheJS/fastOptJS testsJS/test:fastOptJS js/test:fastOptJS
}

jvmCompile() {
  sbt ++$SCALA_VERSION jvm/compile jvm/test:compile
}

runJsTests() {
  sbt ++$SCALA_VERSION js/test
}

runJvmTests() {
  if [ "$(uname)" == "Darwin" ]; then
    IT="testsJVM/it:test" # don't run proxy-tests in particular
  else
    IT="jvm/it:test"
  fi

  sbt ++$SCALA_VERSION jvm/test $IT
}

validateReadme() {
  # check that tut runs fine, and that the README doesn't change after a `sbt tut`
  mv README.md README.md.orig


  if is212; then
    TUT_SCALA_VERSION="2.12.1" # Later versions seem to make tut not see the coursier binaries
  else
    TUT_SCALA_VERSION="$SCALA_VERSION"
  fi

  sbt ++${TUT_SCALA_VERSION} tut

  if cmp -s README.md.orig README.md; then
    echo "README.md doesn't change"
  else
    echo "Error: README.md not the same after a \"sbt tut\":"
    diff -u README.md.orig README.md
    exit 1
  fi
}

checkBinaryCompatibility() {
  sbt ++${SCALA_VERSION} coreJVM/mimaReportBinaryIssues cacheJVM/mimaReportBinaryIssues
}

publish() {
  sbt ++${SCALA_VERSION} publish
}

testBootstrap() {
  if is212; then
    sbt ++${SCALA_VERSION} "project cli" pack
    cli/target/pack/bin/coursier bootstrap -o cs-echo io.get-coursier:echo:1.0.0
    if [ "$(./cs-echo foo)" != foo ]; then
      echo "Error: unexpected output from bootstrapped echo command." 1>&2
      exit 1
    fi
  fi
}

testNativeBootstrap() {
  if is212 && [ "$NATIVE" = "1" ]; then
    sbt ++${SCALA_VERSION} "project cli" pack
    cli/target/pack/bin/coursier bootstrap -S -o native-echo io.get-coursier:echo_native0.3_2.11:1.0.1
    if [ "$(./native-echo -n foo a)" != "foo a" ]; then
      echo "Error: unexpected output from native test bootstrap." 1>&2
      exit 1
    fi
  fi
}

addPgpKeys() {
  for key in b41f2bce 9fa47a44 ae548ced b4493b94 53a97466 36ee59d9 dc426429 3b80305d 69e0a56c fdd5c0cd 35543c27 70173ee5 111557de 39c263a9; do
    gpg --keyserver keyserver.ubuntu.com --recv "$key"
  done
}


# TODO Add coverage once https://github.com/scoverage/sbt-scoverage/issues/111 is fixed

downloadInstallSbtExtras
setupCoursierBinDir

if isScalaJs; then
  jsCompile
  runJsTests
else
  testNativeBootstrap

  integrationTestsRequirements
  jvmCompile

  if sbtCoursier; then
    if is210 || is212; then
      runSbtCoursierTests
    fi
  elif sbtShading; then
    if is210 || is212; then
      runSbtShadingTests
    fi
  else
    # Required for the proxy tests (currently CentralNexus2ProxyTests and CentralNexus3ProxyTests)
    launchProxyRepos

    runJvmTests

    testBootstrap

    validateReadme
    checkBinaryCompatibility
  fi
fi


if [ "$PUBLISH" = 1 -a "$PULL_REQUEST" = false -a "$BRANCH" = master ]; then
  publish

  if is211 && isScalaJs; then
    #"$(dirname "$0")/push-gh-pages.sh" "$SCALA_VERSION"
    :
  fi
fi

