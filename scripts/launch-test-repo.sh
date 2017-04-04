#!/usr/bin/env bash
set -e

VERSION="${VERSION:-1.0.0-SNAPSHOT}"

cd "$(dirname "$0")/.."

./coursier launch \
  "io.get-coursier:http-server-java7_2.11:$VERSION" \
  -r https://dl.bintray.com/scalaz/releases \
  -- \
    -d tests/jvm/src/test/resources/test-repo/http/abc.com \
    -u user -P pass -r realm \
    -v \
    "$@" &
