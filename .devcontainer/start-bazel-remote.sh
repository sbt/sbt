#!/bin/bash
set -euo pipefail

if curl -sf http://localhost:8000/status > /dev/null; then
  echo "bazel-remote is already running"
  exit 0
fi

mkdir -p "$HOME/bazel-remote/temp"
nohup setsid bazel-remote --max_size 5 --dir "$HOME/bazel-remote/temp" \
  --http_address localhost:8000 --grpc_address localhost:2024 \
  > /tmp/bazel-remote.log 2>&1 < /dev/null &
echo $! > /tmp/bazel-remote.pid

for i in $(seq 1 30); do
  curl -sf http://localhost:8000/status > /dev/null && exit 0
  sleep 1
done
echo "bazel-remote did not start in time"
cat /tmp/bazel-remote.log
exit 1
