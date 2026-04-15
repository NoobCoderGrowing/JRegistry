#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for f in "$SCRIPT_DIR/logs/node1.pid" "$SCRIPT_DIR/logs/node2.pid" "$SCRIPT_DIR/logs/node3.pid"; do
  if [ -f "$f" ]; then
    pid=$(cat "$f")
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill -9 "$pid"
      echo "Stopped pid=$pid"
    fi
  fi
done