#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
VERSION="1.0.1"
RELEASE_DIR="$ROOT_DIR/release/JRegistry-${VERSION}"
ADMIN_UI_DIR="$SCRIPT_DIR/admin-ui"

echo "==> 1. Build admin-ui"
if [ ! -d "$ADMIN_UI_DIR/node_modules" ]; then
  npm --prefix "$ADMIN_UI_DIR" install
fi
npm --prefix "$ADMIN_UI_DIR" run build

echo "==> 2. Build jar"
mvn -f "$ROOT_DIR/pom.xml" clean package -pl JRegistry -am -DskipTests

echo "==> 3. Assemble release directory"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR/config" "$RELEASE_DIR/persistency" "$RELEASE_DIR/logs"

cp "$SCRIPT_DIR/target/JRegistry-${VERSION}.jar" "$RELEASE_DIR/"
cp "$SCRIPT_DIR/src/main/resources/application.yaml" "$RELEASE_DIR/config/"
cp "$SCRIPT_DIR/src/main/resources/application_node2.yaml" "$RELEASE_DIR/config/"
cp "$SCRIPT_DIR/src/main/resources/application_node3.yaml" "$RELEASE_DIR/config/"

# 若有 release 专用启动脚本则复制，否则复制后需改路径
cp "$SCRIPT_DIR/start-release.sh" "$RELEASE_DIR/start.sh" 2>/dev/null || true
cp "$SCRIPT_DIR/start-cluster-release.sh" "$RELEASE_DIR/start-cluster.sh" 2>/dev/null || true
cp "$SCRIPT_DIR/stop-release.sh" "$RELEASE_DIR/stop.sh" 2>/dev/null || true

echo "==> 4. Set permissions"
chmod -R a+rwx "$ROOT_DIR/release"

echo "==> 5. Create tarball"
cd "$ROOT_DIR/release"
tar -czf "JRegistry-${VERSION}.tar.gz" "JRegistry-${VERSION}"

echo "Done: $ROOT_DIR/release/JRegistry-${VERSION}.tar.gz"
ls -la "$RELEASE_DIR"