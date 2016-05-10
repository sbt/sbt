#!/bin/bash

if [ "$1" = "clone" ]; then
  git clone "https://${GH_TOKEN}@github.com/alexarchambault/coursier.git" -b gh-pages coursier-gh-pages >/dev/null 2>&1
else
  git push origin gh-pages >/dev/null 2>&1
fi
