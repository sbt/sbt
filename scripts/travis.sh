#!/usr/bin/env bash
set -evx

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

integrationTestsRequirements() {
  # Required for ~/.ivy2/local repo tests
  sbt scala211 coreJVM/publishLocal scala212 cli/publishLocal

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
  if [ "$SCALA_VERSION" = "2.10" ]; then
    sbt scalaFromEnv "sbt-coursier/scripted sbt-coursier/*" "sbt-coursier/scripted sbt-coursier-0.13/*"
  else
    sbt scalaFromEnv "sbt-coursier/scripted sbt-coursier/simple" # full scripted suite currently taking too long on Travis CI...
  fi
  sbt scalaFromEnv sbt-pgp-coursier/scripted
}

runSbtShadingTests() {
  sbt scalaFromEnv "sbt-shading/scripted sbt-shading/*"
  if [ "$SCALA_VERSION" = "2.10" ]; then
    sbt scalaFromEnv "sbt-shading/scripted sbt-shading-0.13/*"
  fi
}

jsCompile() {
  sbt scalaFromEnv js/compile js/test:compile coreJS/fastOptJS cacheJS/fastOptJS testsJS/test:fastOptJS js/test:fastOptJS
}

jvmCompile() {
  sbt scalaFromEnv jvm/compile jvm/test:compile
}

runJsTests() {
  sbt scalaFromEnv js/test
}

runJvmTests() {
  if [ "$(uname)" == "Darwin" ]; then
    IT="testsJVM/it:test" # don't run proxy-tests in particular
  else
    IT="jvm/it:test"
  fi

  sbt scalaFromEnv jvm/test $IT
}

validateReadme() {
  # check that tut runs fine, and that the README doesn't change after a `sbt tut`
  mv README.md README.md.orig


  if [ "$SCALA_VERSION" = 2.12 ]; then
    # Later 2.12 versions seem to make tut not see the coursier binaries
    sbt '++2.12.1!' tut
  else
    sbt scalaFromEnv tut
  fi

  if cmp -s README.md.orig README.md; then
    echo "README.md doesn't change"
  else
    echo "Error: README.md not the same after a \"sbt tut\":"
    diff -u README.md.orig README.md
    exit 1
  fi
}

checkBinaryCompatibility() {
  sbt scalaFromEnv coreJVM/mimaReportBinaryIssues cacheJVM/mimaReportBinaryIssues
}

publish() {
  sbt scalaFromEnv publish
}

testBootstrap() {
  if [ "$SCALA_VERSION" = 2.12 ]; then
    sbt scalaFromEnv "project cli" pack
    cli/target/pack/bin/coursier bootstrap -o cs-echo io.get-coursier:echo:1.0.0
    if [ "$(./cs-echo foo)" != foo ]; then
      echo "Error: unexpected output from bootstrapped echo command." 1>&2
      exit 1
    fi
  fi
}

testNativeBootstrap() {
  if [ "$SCALA_VERSION" = "2.12" -a "$NATIVE" = "1" ]; then
    sbt scalaFromEnv "project cli" pack
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
    if [ "$SCALA_VERSION" = "2.10" -o "$SCALA_VERSION" = "2.12" ]; then
      runSbtCoursierTests
    fi
  elif sbtShading; then
    if [ "$SCALA_VERSION" = "2.10" -o "$SCALA_VERSION" = "2.12" ]; then
      runSbtShadingTests
    fi
  else
    runJvmTests

    testBootstrap

    validateReadme
    checkBinaryCompatibility
  fi
fi


PULL_REQUEST="${PULL_REQUEST:-${TRAVIS_PULL_REQUEST:-false}}"
BRANCH="${BRANCH:-${TRAVIS_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}}"
PUBLISH="${PUBLISH:-0}"

if [ "$PUBLISH" = 1 -a "$PULL_REQUEST" = false -a "$BRANCH" = master ]; then
  publish

  if [ "$SCALA_VERSION" = "2.11" ] && isScalaJs; then
    #"$(dirname "$0")/push-gh-pages.sh" "$SCALA_VERSION"
    :
  fi
fi

