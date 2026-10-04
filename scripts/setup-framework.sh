#!/usr/bin/env bash
#
# 拉取并安装 JNimble 框架到本地仓库(插件编译依赖它)。
#
# 框架源码放在本仓库根目录下的 `jnimble-framework/`,该目录**不被本仓库跟踪**
# (见 .gitignore),由本脚本负责获取,保持插件仓库本身只包含插件代码。
#
# 用法:
#   bash scripts/setup-framework.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRAMEWORK_DIR="$REPO_ROOT/jnimble-framework"
FRAMEWORK_REPO="${JNIMBLE_FRAMEWORK_REPO:-https://github.com/what123/JNimble.git}"

if [[ -d "$FRAMEWORK_DIR/.git" || -f "$FRAMEWORK_DIR/.git" ]]; then
  echo "==> 更新已存在的框架 checkout: $FRAMEWORK_DIR"
  git -C "$FRAMEWORK_DIR" fetch --all --tags
  git -C "$FRAMEWORK_DIR" pull --ff-only || true
else
  echo "==> 克隆框架 $FRAMEWORK_REPO -> $FRAMEWORK_DIR"
  rm -rf "$FRAMEWORK_DIR"
  git clone "$FRAMEWORK_REPO" "$FRAMEWORK_DIR"
fi

echo "==> 安装框架到本地 Maven 仓库"
mvn -f "$FRAMEWORK_DIR/pom.xml" clean install -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true

echo
echo "完成。接下来构建插件:"
echo "  bash scripts/build-plugins.sh"
