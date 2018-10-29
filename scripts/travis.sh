#!/usr/bin/env bash
set -euvx

downloadInstallSbtExtras() {
  mkdir -p bin
  curl -L -o bin/sbt https://github.com/paulp/sbt-extras/raw/9ade5fa54914ca8aded44105bf4b9a60966f3ccd/sbt
  chmod +x bin/sbt
}

sbtPgpCoursier() {
  [ "${SBT_PGP_COURSIER:-""}" = 1 ]
}

sbtShading() {
  [ "${SBT_SHADING:-""}" = 1 ]
}

runSbtCoursierTests() {
  ./metadata/scripts/with-test-repo.sh sbt ++$TRAVIS_SCALA_VERSION sbt-coursier/test "sbt-coursier/scripted sbt-coursier-group-$SBT_COURSIER_TEST_GROUP/*"
}

runSbtShadingTests() {
  sbt ++$TRAVIS_SCALA_VERSION sbt-shading/scripted
}

runSbtPgpCoursierTests() {
  addPgpKeys
  sbt ++$TRAVIS_SCALA_VERSION sbt-pgp-coursier/scripted
}

addPgpKeys() {
  for key in b41f2bce 9fa47a44 ae548ced b4493b94 53a97466 36ee59d9 dc426429 3b80305d 69e0a56c fdd5c0cd 35543c27 70173ee5 111557de 39c263a9; do
    gpg --keyserver keyserver.ubuntu.com --recv "$key"
  done
}


downloadInstallSbtExtras

if sbtShading; then
  runSbtShadingTests
elif sbtPgpCoursier; then
  runSbtPgpCoursierTests
else
  runSbtCoursierTests
fi

