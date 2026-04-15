#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_JAR="$SCRIPT_DIR/target/JRegistryClient-1.0-SNAPSHOT.jar"
LOG_DIR="$SCRIPT_DIR/logs"

mkdir -p "$LOG_DIR"

"$SCRIPT_DIR/stop.sh" || true

sleep 1

nohup java -jar "$APP_JAR" \
  > /dev/null 2>&1 &
echo $! > "$LOG_DIR/client.pid"

echo "Started JRegistryClient pid=$(cat "$LOG_DIR/client.pid")"
