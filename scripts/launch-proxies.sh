#!/usr/bin/env bash
set -e

MAX_WAIT=120

wait_for() {
  TARGET="$1"
  I=0
  while ! curl "$TARGET"; do
    if [ "$I" -gt "$MAX_WAIT" ]; then
      echo "$TARGET not available after $MAX_WAIT seconds" 1>&2
      exit 1
    fi

    I="$(( $I + 1 ))"
    sleep 1
  done
}

docker run -d -p 9081:8081 --name nexus sonatype/nexus:2.14.4
wait_for "http://localhost:9081/nexus/content/repositories/central/"

docker run -d -p 9082:8081 --name nexus3 sonatype/nexus3:3.3.1
wait_for "http://localhost:9082/repository/maven-central/"
