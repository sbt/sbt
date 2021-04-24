#!/bin/bash -x

# exit when something fails
set -e

## https://github.com/travis-ci/travis-ci/issues/8408
unset _JAVA_OPTIONS
unset SBT_OPTS

java -version
## end of Java switching

rm -rf freshly-baked
mkdir -p freshly-baked
unzip ../target/universal/sbt.zip -d ./freshly-baked

./freshly-baked/sbt/bin/sbt -Dsbt.no.format=true about
./freshly-baked/sbt/bin/sbt -Dsbt.no.format=true about 1> output.txt 2> err.txt
./freshly-baked/sbt/bin/sbt check

./freshly-baked/sbt/bin/sbt about run -v

./freshly-baked/sbt/bin/sbt about run

fail() {
    echo "$@" >&2
    exit 1
}

# env HOME=./target/home1 ./freshly-baked/sbt/bin/sbt about
# test -d ./target/home1/.sbt/preloaded/org/scala-sbt || fail "expected to find preloaded in ./target/home1/.sbt"

# env HOME=./target/home2 ./freshly-baked/sbt/bin/sbt -sbt-dir ./target/home2/alternate-sbt about
# test -d ./target/home2/alternate-sbt/preloaded/org/scala-sbt || fail "expected to find preloaded in ./target/home2/alternate-sbt"

# env HOME=./target/home3 ./freshly-baked/sbt/bin/sbt -J-Dsbt.preloaded=./target/home3/alternate-preloaded about
# test -d ./target/home3/alternate-preloaded/org/scala-sbt || fail "expected to find preloaded in ./target/home3/alternate-preloaded"

# env HOME=./target/home4 ./freshly-baked/sbt/bin/sbt -J-Dsbt.global.base=./target/home4/global-base about
# test -d ./target/home4/global-base/preloaded/org/scala-sbt || fail "expected to find preloaded in ./target/home4/global-base"
