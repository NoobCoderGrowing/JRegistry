#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_JAR="$SCRIPT_DIR/target/JRegistry-1.0.0.jar"
LOG_DIR="$SCRIPT_DIR/logs"
ADMIN_UI_DIR="$SCRIPT_DIR/admin-ui"

ensure_node_npm() {
  if command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
    echo "Node.js $(node -v), npm $(npm -v) already installed"
    return 0
  fi

  echo "Node.js/npm not found, installing..."

  if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y nodejs npm
  else
    echo "Error: cannot auto-install nodejs/npm (apt-get not found)."
    echo "Please install Node.js manually: https://nodejs.org/"
    exit 1
  fi

  if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    echo "Error: nodejs/npm installation failed."
    exit 1
  fi

  echo "Installed Node.js $(node -v), npm $(npm -v)"
}

# 1. 构建前端（输出到 src/main/resources/static）
ensure_node_npm
if [ ! -d "$ADMIN_UI_DIR/node_modules" ]; then
  echo "Installing admin-ui dependencies..."
  npm --prefix "$ADMIN_UI_DIR" install
fi
echo "Building admin-ui..."
npm --prefix "$ADMIN_UI_DIR" run build

# 2. 构建后端 jar（包含最新 static 资源）
echo "Building JRegistry..."
mvn -f "$ROOT_DIR/pom.xml" clean package -pl JRegistry -am -DskipTests

mkdir -p "$LOG_DIR"

"$SCRIPT_DIR/stop.sh" || true

sleep 1

rm -rf "$LOG_DIR"/*

# 3. 在项目根目录启动，保证 persistency/ 相对路径正确
cd "$ROOT_DIR"

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