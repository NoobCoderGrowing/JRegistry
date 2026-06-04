#!/usr/bin/env bash
set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/logs"

stop_pid_file() {
  local pid_file="$1"
  local name
  name=$(basename "$pid_file" .pid)

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

found=0
for pid_file in "$LOG_DIR"/*.pid; do
  [ -f "$pid_file" ] || continue
  found=1
  stop_pid_file "$pid_file"
done

if [ "$found" -eq 0 ]; then
  echo "No pid files found in $LOG_DIR"
fi

echo "All nodes stopped."
