#!/usr/bin/env bash
set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/logs"

stop_node() {
  local name="$1"
  local pid_file="$LOG_DIR/${name}.pid"

  if [ ! -f "$pid_file" ]; then
    echo "${name}: not running (no pid file)"
    return 0
  fi

  local pid
  pid=$(cat "$pid_file")

  if kill -0 "$pid" >/dev/null 2>&1; then
    kill -9 "$pid"
    echo "${name}: stopped pid=$pid"
  else
    echo "${name}: stale pid=$pid"
  fi

  rm -f "$pid_file"
}

mkdir -p "$LOG_DIR"

stop_node "node1"
stop_node "node2"
stop_node "node3"

echo "All nodes stopped."