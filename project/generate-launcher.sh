#!/bin/bash

VERSION=1.0.0-M1
CACHE_VERSION=v1

"$(dirname "$0")/../cli/target/pack/bin/coursier" bootstrap \
  com.github.alexarchambault:coursier-cli_2.11:$VERSION \
  -V com.github.alexarchambault:coursier_2.11:$VERSION \
  -V com.github.alexarchambault:coursier-cache_2.11:$VERSION \
  -D "\${user.home}/.coursier/bootstrap/$VERSION" \
  -r https://repo1.maven.org/maven2 \
  -r https://oss.sonatype.org/content/repositories/releases \
  -r https://oss.sonatype.org/content/repositories/snapshots \
  -b \
  -f -o coursier \
  -M coursier.cli.Coursier \
  -P coursier.cache="\${user.home}/.coursier/cache/$CACHE_VERSION"
