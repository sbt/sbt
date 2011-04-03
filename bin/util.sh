#!/usr/bin/env bash
#

function camelCase () {
  # pretty sad having to resort to this in 2011
  SED=""
  if [ -f /usr/local/bin/gsed ]; then
    SED=/usr/local/bin/gsed
  else
    SED=sed
  fi

  echo $1 | $SED -e 's/[-_]\([a-z]\)/\u\1/g' | $SED -e 's/^./\u&/;'
}
