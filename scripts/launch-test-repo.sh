#!/usr/bin/env bash
set -e

VERSION="1.0.0"

cd "$(dirname "$0")/.."

# synchronously fill cache so that two runs of this script don't try to download
# a same file at the same time (and one of them fail because of locks)
./coursier fetch \
  "io.get-coursier:http-server_2.12:$VERSION" \
  -r https://dl.bintray.com/scalaz/releases

./coursier launch \
  "io.get-coursier:http-server_2.12:$VERSION" \
  -- \
    -d tests/jvm/src/test/resources/test-repo/http/abc.com \
    -u user -P pass -r realm \
    -v \
    "$@" &
