#!/bin/bash
set -ev

TRAVIS_SCALA_VERSION="$1"
shift

sbt ++${TRAVIS_SCALA_VERSION} web/fastOptJS

HELPER="$(readlink -f "$(dirname "$0")/push-gh-pages-helper.sh")"

"$HELPER" clone
cd coursier-gh-pages

[ ! -e staging ] || git rm -r staging

mkdir staging
DIR="$(for i in "../web/target/scala-"*; do echo "$i"; done)"
cp "$DIR/web-"*.js* staging
cp "$DIR/classes/index"*.html staging
cp -R "$DIR/classes/css" staging

for i in staging/*.html; do
  mv "$i" "$i.0"
  sed "s/src=\"\.\.\/web-/src=\"web-/g" < "$i.0" > "$i"
  rm -f "$i.0"
done

git config user.name "Travis-CI"
git config user.email "invalid@travis-ci.com"
git add staging
git commit -m "Deploy to gh-pages"
"$HELPER"
