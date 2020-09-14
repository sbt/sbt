#!/usr/bin/env bash
set -euvx

lmCoursier() {
  [ "${LM_COURSIER:-""}" = 1 ]
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
    evictionCheck \
    compatibilityCheck \
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
    sbt-coursier-shared/test \
    "sbt-coursier/scripted shared-$TEST_GROUP/* $SCRIPTED_EXTRA"
}

runSbtShadingTests() {
  ./sbt sbt-shading/scripted
}


if sbtShading; then
  runSbtShadingTests
elif lmCoursier; then
  runLmCoursierTests
else
  runSbtCoursierTests
fi

