#!/usr/bin/env bash
set -e

APP_JAR="target/JRegistryCenter-1.0-SNAPSHOT.jar"
LOG_DIR="logs"

mkdir -p "$LOG_DIR"


# 启动 3 个节点（使用不同配置）
nohup java -jar "$APP_JAR" --spring.config.location=classpath:/application.yaml \
  > "$LOG_DIR/node1.log" 2>&1 &
echo $! > "$LOG_DIR/node1.pid"

nohup java -jar "$APP_JAR" --spring.config.location=classpath:/application_node2.yaml \
  > "$LOG_DIR/node2.log" 2>&1 &
echo $! > "$LOG_DIR/node2.pid"

nohup java -jar "$APP_JAR" --spring.config.location=classpath:/application_node3.yaml \
  > "$LOG_DIR/node3.log" 2>&1 &
echo $! > "$LOG_DIR/node3.pid"

echo "Started nodes:"
echo "node1 pid=$(cat "$LOG_DIR/node1.pid")"
echo "node2 pid=$(cat "$LOG_DIR/node2.pid")"
echo "node3 pid=$(cat "$LOG_DIR/node3.pid")"