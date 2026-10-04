#!/usr/bin/env bash
#
# 开发辅助:构建全部业务插件,并把插件 JAR 输出到框架 starter 的插件目录。
#
# 设计(方案A):starter 不依赖任何业务插件,classpath 保持干净;插件通过
# jnimble-starter/data/plugins/ 目录以 JAR 方式安装(dev-classpath-enabled=false)。
# 依赖方向始终是 "插件 -> 平台",平台侧不感知具体插件。
#
# 用法:
#   bash scripts/build-plugins.sh                 # 构建全部插件并落盘
#   bash scripts/build-plugins.sh -pl jnimble-plugin-order-core   # 只构建某个插件
#
# 首次使用需先把框架安装到本地仓库(一次即可):
#   cd jnimble-framework && mvn clean install -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true
#
# 运行应用(运行中改动插件时,拷入的新 JAR 会被 PluginDirectoryWatcher 热部署):
#   cd jnimble-framework && mvn -pl jnimble-starter spring-boot:run
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_DIR="$REPO_ROOT/jnimble-framework/jnimble-starter/data/plugins"

cd "$REPO_ROOT"
mvn -f plugins/pom.xml package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true "$@"

echo
echo "插件 JAR 输出目录: $PLUGIN_DIR"
ls -1 "$PLUGIN_DIR"/*.jar 2>/dev/null | sed 's|.*/||' || true
echo
echo "启动/热部署: cd jnimble-framework && mvn -pl jnimble-starter spring-boot:run"
