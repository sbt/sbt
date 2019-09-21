#!/usr/bin/env bash
set -euvx

lmCoursier() {
  [ "${LM_COURSIER:-""}" = 1 ]
}

sbtPgpCoursier() {
  [ "${SBT_PGP_COURSIER:-""}" = 1 ]
}

sbtShading() {
  [ "${SBT_SHADING:-""}" = 1 ]
}

runLmCoursierTests() {
  if [ "$TEST_GROUP" = 1 ]; then
    SCRIPTED_EXTRA="sbt-lm-coursier/*"
  elif [ "$TEST_GROUP" = 2 ]; then
    SCRIPTED_EXTRA="scala-211/*"
  else
    SCRIPTED_EXTRA=""
  fi

  # publishing locally to ensure shading runs fine
  ./metadata/scripts/with-test-repo.sh ./sbt \
    ++$TRAVIS_SCALA_VERSION! \
    mimaReportBinaryIssues \
    lm-coursier-shaded/publishLocal \
    lm-coursier/test \
    "sbt-lm-coursier/scripted shared-$TEST_GROUP/* $SCRIPTED_EXTRA"
}

runSbtCoursierTests() {
  if [ "$TEST_GROUP" = 1 ]; then
    SCRIPTED_EXTRA="sbt-coursier/*"
  elif [ "$TEST_GROUP" = 2 ]; then
    SCRIPTED_EXTRA="scala-211/*"
  else
    SCRIPTED_EXTRA=""
  fi

  ./metadata/scripts/with-test-repo.sh ./sbt \
    ++$TRAVIS_SCALA_VERSION \
    sbt-coursier-shared/test \
    "sbt-coursier/scripted shared-$TEST_GROUP/* $SCRIPTED_EXTRA"
}

runSbtShadingTests() {
  ./sbt ++$TRAVIS_SCALA_VERSION sbt-shading/scripted
}

runSbtPgpCoursierTests() {
  addPgpKeys
  ./sbt ++$TRAVIS_SCALA_VERSION sbt-pgp-coursier/compile
  # disable for now, sbt ++$TRAVIS_SCALA_VERSION sbt-pgp-coursier/scripted
  # fails with "unknown signature key algorithm: 22"
  # and "No signatures found"
}

addPgpKeys() {
  for key in b41f2bce 9fa47a44 ae548ced b4493b94 53a97466 36ee59d9 dc426429 3b80305d 69e0a56c fdd5c0cd 35543c27 70173ee5 111557de 39c263a9; do
    gpg --keyserver keyserver.ubuntu.com --recv "$key"
  done
}


if sbtShading; then
  runSbtShadingTests
elif sbtPgpCoursier; then
  runSbtPgpCoursierTests
elif lmCoursier; then
  runLmCoursierTests
else
  runSbtCoursierTests
fi

