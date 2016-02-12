#!/bin/bash

VERSION=1.0.0-M7
CACHE_VERSION=v1

SBTPACK_LAUNCHER="$(dirname "$0")/../cli/target/pack/bin/coursier"

if [ ! -f "$SBTPACK_LAUNCHER" ]; then
  sbt cli/pack
fi

"$SBTPACK_LAUNCHER" bootstrap \
  com.github.alexarchambault:coursier-cli_2.11:$VERSION \
  --no-default \
  -r central \
  -r sonatype:releases \
  -D "\${user.home}/.coursier/bootstrap/$VERSION" \
  -f -o coursier \
  -M coursier.cli.Coursier \
  -P coursier.cache="\${user.home}/.coursier/cache/$CACHE_VERSION"
