#!/usr/bin/env bash
set -e

VERSION=1.0.0-RC2

"$(dirname "$0")/../coursier" bootstrap \
  "io.get-coursier:sbt-launcher_2.12:$VERSION" \
  -r sonatype:releases \
  --no-default \
  -i launcher \
  -I launcher:org.scala-sbt:launcher-interface:1.0.0 \
  -o csbt \
  -J -Djline.shutdownhook=false \
  "$@"
