#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_JAR="$SCRIPT_DIR/target/JRegistryCenter-1.0-SNAPSHOT.jar"
LOG_DIR="$SCRIPT_DIR/logs"

mkdir -p "$LOG_DIR"

"$SCRIPT_DIR/stop.sh" || true

sleep 1

rm -rf "$LOG_DIR"/*

# 启动 3 个节点（使用不同配置）
nohup java -jar "$APP_JAR" --spring.config.location=classpath:/application.yaml \
  > /dev/null 2>&1 &
echo $! > "$LOG_DIR/node1.pid"

nohup java -jar "$APP_JAR" --spring.config.location=classpath:/application_node2.yaml \
  > /dev/null 2>&1 &
echo $! > "$LOG_DIR/node2.pid"

nohup java -jar "$APP_JAR" --spring.config.location=classpath:/application_node3.yaml \
  > /dev/null 2>&1 &
echo $! > "$LOG_DIR/node3.pid"

echo "Started nodes:"
echo "node1 pid=$(cat "$LOG_DIR/node1.pid")"
echo "node2 pid=$(cat "$LOG_DIR/node2.pid")"
echo "node3 pid=$(cat "$LOG_DIR/node3.pid")"