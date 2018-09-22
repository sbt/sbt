#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/.."

SERVER_PID=""

cleanup() {
  if [ ! -z "$SERVER_PID" ]; then
    echo "Terminating background HTTP server"
    kill -15 "$SERVER_PID"
    while kill -0 "$SERVER_PID" >/dev/null 2>&1; do
      echo "Server still running"
      sleep 1
      kill -15 "$SERVER_PID" >/dev/null 2>&1 || true
    done
    echo "Server terminated"
  fi
}

trap cleanup EXIT INT TERM

export TEST_REPOSITORY_HOST="localhost"
export TEST_REPOSITORY_PORT="8080"
export TEST_REPOSITORY_USER="user"
export TEST_REPOSITORY_PASSWORD="pass"

export TEST_REPOSITORY="http://$TEST_REPOSITORY_HOST:$TEST_REPOSITORY_PORT"

# see https://unix.stackexchange.com/questions/90244/bash-run-command-in-background-and-capture-pid
runServerBg() {
  java -jar ./coursier launch \
    "io.get-coursier:http-server_2.12:1.0.0" \
    -- \
      -d tests/jvm/src/test/resources/test-repo/http/abc.com \
      -u "$TEST_REPOSITORY_USER" -P "$TEST_REPOSITORY_PASSWORD" -r realm \
      -v \
      --host "$TEST_REPOSITORY_HOST" \
      --port "$TEST_REPOSITORY_PORT" &
  SERVER_PID="$!"
}

runServerBg

"$@"
