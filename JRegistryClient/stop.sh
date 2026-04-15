#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
PID_FILE="$LOG_DIR/client.pid"

if [ -f "$PID_FILE" ]; then
  pid=$(cat "$PID_FILE")
  if kill -0 "$pid" >/dev/null 2>&1; then
    kill -9 "$pid"
    echo "Stopped pid=$pid"
  fi
fi

rm -rf "$LOG_DIR"
mkdir -p "$LOG_DIR"
