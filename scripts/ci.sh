#!/usr/bin/env bash
set -euvx

./scripts/cs-setup.sh
mkdir -p bin
./cs bootstrap -o bin/sbt sbt-launcher io.get-coursier:coursier_2.12:2.0.0-RC6-25
rm -f cs cs.exe

if [ "$(expr substr $(uname -s) 1 5 2>/dev/null)" == "Linux" ]; then
  SBT="./bin/sbt -C--plugin-version=2.0.0-RC6-8"
elif [ "$(uname)" == "Darwin" ]; then
  SBT="./bin/sbt -C--plugin-version=2.0.0-RC6-8"
else
  SBT="./bin/sbt.bat -C--plugin-version=2.0.0-RC6-8"
fi

lmCoursier() {
  [ "${PLUGIN:-""}" = "sbt-lm-coursier" ]
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
  ./metadata/scripts/with-test-repo.sh $SBT \
    evictionCheck \
    compatibilityCheck \
    +lm-coursier-shaded/publishLocal \
    +lm-coursier/test \
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

  ./metadata/scripts/with-test-repo.sh $SBT \
    sbt-coursier-shared/test \
    "sbt-coursier/scripted shared-$TEST_GROUP/* $SCRIPTED_EXTRA"
}


if lmCoursier; then
  runLmCoursierTests
else
  runSbtCoursierTests
fi

