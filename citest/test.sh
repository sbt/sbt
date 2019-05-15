#!/bin/bash

## https://github.com/travis-ci/travis-ci/issues/8408
unset _JAVA_OPTIONS

java -version
## end of Java switching

mkdir -p freshly-baked
unzip -qo ../target/universal/sbt.zip -d ./freshly-baked

export SBT_OPTS=-Dfile.encoding=UTF-8

./freshly-baked/sbt/bin/sbt about run -v

export SBT_OPTS="-Dfile.encoding=UTF-8 -Xms2048M -Xmx2048M -Xss2M -XX:MaxPermSize=512M"

./freshly-baked/sbt/bin/sbt about run

fail() {
    echo "$@" >&2
    exit 1
}

env HOME=./target/home1 ./freshly-baked/sbt/bin/sbt about
test -d ./target/home1/.sbt/preloaded/org/scala-sbt || fail "expected to find preloaded in ./target/home1/.sbt"

env HOME=./target/home2 ./freshly-baked/sbt/bin/sbt -sbt-dir ./target/home2/alternate-sbt about
test -d ./target/home2/alternate-sbt/preloaded/org/scala-sbt || fail "expected to find preloaded in ./target/home2/alternate-sbt"

env HOME=./target/home3 ./freshly-baked/sbt/bin/sbt -J-Dsbt.preloaded=./target/home3/alternate-preloaded about
test -d ./target/home3/alternate-preloaded/org/scala-sbt || fail "expected to find preloaded in ./target/home3/alternate-preloaded"

env HOME=./target/home4 ./freshly-baked/sbt/bin/sbt -J-Dsbt.global.base=./target/home4/global-base about
test -d ./target/home4/global-base/preloaded/org/scala-sbt || fail "expected to find preloaded in ./target/home4/global-base"

