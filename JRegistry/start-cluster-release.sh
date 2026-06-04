#!/usr/bin/env bash
set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_JAR="$BASE_DIR/JRegistry-1.0.0.jar"
LOG_DIR="$BASE_DIR/logs"
CONFIG_DIR="$BASE_DIR/config"

mkdir -p "$LOG_DIR" "$BASE_DIR/persistency"

"$BASE_DIR/stop.sh" || true
sleep 1

rm -f "$LOG_DIR"/*

# 在 release 根目录启动，persistency/ 相对路径才正确
cd "$BASE_DIR"

nohup java -jar "$APP_JAR" \
  --spring.config.location=file:${CONFIG_DIR}/application.yaml \
  > /dev/null 2>&1 &
echo $! > "$LOG_DIR/node1.pid"

nohup java -jar "$APP_JAR" \
  --spring.config.location=file:${CONFIG_DIR}/application_node2.yaml \
  > /dev/null 2>&1 &
echo $! > "$LOG_DIR/node2.pid"

nohup java -jar "$APP_JAR" \
  --spring.config.location=file:${CONFIG_DIR}/application_node3.yaml \
  > /dev/null 2>&1 &
echo $! > "$LOG_DIR/node3.pid"

echo "Started from $BASE_DIR"