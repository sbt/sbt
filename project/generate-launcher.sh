#!/bin/bash

"$(dirname "$0")/../cli/target/pack/bin/coursier" bootstrap \
  com.github.alexarchambault:coursier-cli_2.11:0.1.0-SNAPSHOT \
  -D "\$(cd \$(dirname \"\$0\"); pwd)/.coursier/0.1.0-SNAPSHOT-0f15f7f/bootstrap" \
  -r https://repo1.maven.org/maven2 -r https://oss.sonatype.org/content/repositories/snapshots \
  -b \
  -f -o coursier \
  -M coursier.cli.Coursier \
  -e COURSIER_CACHE="\$(cd \$(dirname \"\$0\"); pwd)/.coursier/0.1.0-SNAPSHOT-0f15f7f"
