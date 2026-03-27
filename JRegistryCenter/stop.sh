#!/usr/bin/env bash
set -e

for f in logs/node1.pid logs/node2.pid logs/node3.pid; do
  if [ -f "$f" ]; then
    pid=$(cat "$f")
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid"
      echo "Stopped pid=$pid"
    fi
    rm -f "$f"
  fi
done