# jnimble-order-plugin

JNimble 点餐插件 — 基于 [JNimble](https://github.com/what123/JNimble) 插件化后台框架的点餐管理插件。

## 特性

- 菜单 / 商品 / 规格管理（占位，按需扩展）
- 订单状态流转
- 独立迁移脚本（Flyway 自动执行）
- 独立 i18n 资源包（中英文）
- 与主框架解耦，可独立编译

## 环境要求

- JDK 21+
- Maven 3.9+
- JNimble 主框架（已通过 git submodule 自动拉取）

## 快速开始

### 二开模式（推荐：同时拉取主框架源码）

```bash
git clone --recurse-submodules git@github.com:what123/jnimble-order-plugin.git
cd jnimble-order-plugin
mvn package
```

submodule 会把主框架源码拉到 `jnimble-framework/` 目录，pom 里的 `<relativePath>` 会让插件编译时优先使用本地源码，方便二开调试。

如果已经 clone 但忘了带 `--recurse-submodules`：

```bash
git submodule update --init --recursive
```

### 快速使用模式（只拉插件）

```bash
git clone git@github.com:what123/jnimble-order-plugin.git
cd jnimble-order-plugin
git submodule update --init    # 仍然需要主框架 reactor 才能编译
mvn package
```

### 安装到 JNimble

```bash
cp target/jnimble-order-plugin-*.jar <JNimble 工作目录>/data/plugins/
```

将打好的 JAR 丢入主框架的插件目录，`PluginDirectoryWatcher` 会自动热部署。首次安装进入 `INSTALLED` 状态，在后台「插件管理」点击「启用」即可。

## 目录结构

```
jnimble-order-plugin/
├── jnimble-framework/                    # 主框架 submodule
├── pom.xml                               # parent 指向 ../jnimble-framework
└── src/main/
    ├── java/com/jnimble/order/
    │   └── OrderPluginBoot.java          # 插件入口
    └── resources/
        ├── META-INF/jnimble-plugin.json  # 插件描述符
        ├── templates/plugin/order-plugin/
        │   ├── fragment/sidebar.html     # 侧边栏菜单片段
        │   └── page/index.html           # 插件首页
        ├── i18n/order-plugin*.properties # 国际化
        └── db/migration/plugin/order-plugin/
            └── V1__init.sql              # 数据库迁移脚本
```

## 开发约定

- 模板路径必须以 `plugin/order-plugin/` 开头
- 权限码必须以 `order-plugin.` 开头
- 数据库变更只加新迁移脚本，不改旧脚本（Flyway checksum）
- 详细开发规范参考 [主框架 CLAUDE.md](https://github.com/what123/JNimble/blob/main/CLAUDE.md)

## 授权

Apache 2.0 + 商业授权双重模式。保留版权可商用，去版权需联系 `178277164@qq.com`。详见 [NOTICE](NOTICE)。
