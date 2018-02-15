#!/bin/bash

## https://github.com/travis-ci/travis-ci/issues/8408
export _JAVA_OPTIONS=

## begin Java switching
## swtich to JDK 10 if we've downloaded it
if [ -d ~/jdk-10 ]
then
  JAVA_HOME=~/jdk-10
fi
## include JAVA_HOME into path
PATH=${JAVA_HOME}/bin:$PATH
java -version
## end of Java switching

mkdir freshly-baked
unzip -qo ../target/universal/sbt.zip -d ./freshly-baked

SBT_OPTS=-Dfile.encoding=UTF-8

./freshly-baked/sbt/bin/sbt about run
