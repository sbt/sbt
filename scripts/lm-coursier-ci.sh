#!/usr/bin/env bash
set -euvx

# Force the use of coursier JNI stuff on Windows, which ought to work fine.
# JNI stuff is used to compute the default cache location on Windows (to get the AppData local
# dir, or something like this, via native Windows APIs).
# Without this, if ever coursier fails to load its JNI library on Windows, it falls back
# to using some powershell scripts (via dirs-dev/directories-jvm), which are often a problem,
# see sbt/sbt#5206.
# Enable this once sbt uses the upcoming lm-coursier-shaded version (> 2.0.10-1)
# export COURSIER_JNI="force"

if [ "$(expr substr $(uname -s) 1 5 2>/dev/null)" == "Linux" ]; then
  SBT="sbt"
elif [ "$(uname)" == "Darwin" ]; then
  SBT="sbt"
else
  SBT="sbt.bat"
fi

if [ "$TEST_GROUP" = 1 ]; then
  SCRIPTED_EXTRA="sbt-lm-coursier/*"
elif [ "$TEST_GROUP" = 2 ]; then
  SCRIPTED_EXTRA="scala-211/*"
else
  SCRIPTED_EXTRA=""
fi

# publishing locally to ensure shading runs fine
./lm-coursier/metadata/scripts/with-test-repo.sh $SBT \
  lmCoursierShaded/publishLocal \
  lmCoursier/test \
  # +lm-coursier-shaded/publishLocal \
  # +lm-coursier/test \
  # "sbt-lm-coursier/scripted shared-$TEST_GROUP/* $SCRIPTED_EXTRA"
