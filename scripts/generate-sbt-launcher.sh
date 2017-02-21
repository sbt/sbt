#!/usr/bin/env bash
set -e

COURSIER_VERSION=1.0.0-SNAPSHOT

"$(dirname "$0")/../coursier" bootstrap \
  "io.get-coursier:sbt-launcher_2.12:$COURSIER_VERSION" \
  -i launcher \
  -I launcher:org.scala-sbt:launcher-interface:1.0.0 \
  -o csbt \
  -J -Djline.shutdownhook=false
