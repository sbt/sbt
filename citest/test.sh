#!/bin/bash

## https://github.com/travis-ci/travis-ci/issues/8408
export _JAVA_OPTIONS=

java -version
## end of Java switching

mkdir freshly-baked
unzip -qo ../target/universal/sbt.zip -d ./freshly-baked

export SBT_OPTS=-Dfile.encoding=UTF-8

./freshly-baked/sbt/bin/sbt about run

export SBT_OPTS="-Dfile.encoding=UTF-8 -Xms2048M -Xmx2048M -Xss2M -XX:MaxPermSize=512M"

./freshly-baked/sbt/bin/sbt about run
