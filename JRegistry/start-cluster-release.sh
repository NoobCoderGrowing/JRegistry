#!/usr/bin/env bash
set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/logs"
CONFIG_DIR="$BASE_DIR/config"

# 版本号从随包发布的 jar 解析，升版本时无需改本脚本
APP_JAR="$(ls -1 "$BASE_DIR"/JRegistry-*.jar 2>/dev/null | head -1)"
if [ -z "$APP_JAR" ]; then
  echo "Error: no JRegistry-*.jar found in $BASE_DIR"
  exit 1
fi

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