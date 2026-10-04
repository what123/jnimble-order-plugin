# jnimble-order-plugin

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-21-blue.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/maven-3.9+-blue.svg)](https://maven.apache.org/)
[![Based on JNimble](https://img.shields.io/badge/based%20on-JNimble-blue.svg)](https://github.com/what123/JNimble)

[English](README.md) | 中文

> **基于 [JNimble](https://github.com/what123/JNimble) 开发** —— JNimble 是一个插件化的 Java 后台管理框架。本仓库在其之上提供一套业务插件(点餐、支付、打印、菜单、CRM、License 等)。框架由 `scripts/setup-framework.sh` 拉取到本地 `jnimble-framework/`(被 gitignore,**本仓库只含插件**)。

## 项目简介

`jnimble-order-plugin` 是一个多插件 Maven reactor,为 JNimble 后台框架提供垂直业务能力。所有功能以独立插件 JAR 形式交付——安装即用、卸载即除,**不修改框架源码**。

内置插件清单:

| 插件 | 说明 |
|------|------|
| `jnimble-plugin-order-core` | 点餐核心(后厨排队、订单状态机) |
| `jnimble-plugin-order-table` | 桌台点餐(桌位图、座位路由) |
| `jnimble-plugin-payment` | 支付聚合与对账 |
| `jnimble-plugin-printer-core` | 打印机抽象与模板注册 |
| `jnimble-plugin-printer-feie` | 飞鹅云打印机驱动 |
| `jnimble-plugin-menu-manager` | 菜单 / 商品 / 规格管理 |
| `jnimble-plugin-demo-crm` | CRM 示例插件(hook/route/asset 注册参考实现) |
| `jnimble-plugin-license-issuer` | License 签发后台 UI 与密钥管理(`jnimble-license-sdk` 的管理端) |

## 仓库结构

```
jnimble-order-plugin/
├── plugins/               # 业务插件聚合(pom)
│   ├── pom.xml            # parent -> ../jnimble-framework,dependencyManagement 统一内部版本
│   └── jnimble-plugin-*/  # 各业务插件
├── scripts/               # setup-framework.sh、build-plugins.sh
├── doc/                   # 插件开发指南与相关文档
└── jnimble-framework/     # 本地框架 checkout(被 gitignore,由 scripts/setup-framework.sh 生成)
```

本仓库**只含插件代码**。开发时,`scripts/setup-framework.sh` 会把框架克隆到被忽略的 `jnimble-framework/` 并安装到本地 Maven 仓库,供插件编译使用。

## 环境要求

- JDK 21+
- Maven 3.9+
- MySQL 8+(或任意 Spring Boot 支持的 DataSource)

## 快速开始

### 1. 克隆

```bash
git clone https://github.com/what123/jnimble-order-plugin.git
cd jnimble-order-plugin
```

### 2. 构建 & 本地启动(开发模式,推荐)

starter **不依赖任何业务插件**,classpath 保持干净;插件以 JAR 方式从
`jnimble-framework/jnimble-starter/data/plugins/` 目录安装,由 starter 的
`PluginDirectoryInitializer`(启动扫描)与 `PluginDirectoryWatcher`(运行期热部署)负责加载。
依赖方向始终是"插件 → 平台"。详见 [`doc/dev-workflow.md`](doc/dev-workflow.md)。

首次启动前,拉取并安装 framework 到本地 Maven 仓库(starter 与插件的编译都依赖它):

```bash
bash scripts/setup-framework.sh
# 把 what123/JNimble 克隆到 ./jnimble-framework(被 gitignore)并执行 mvn install
```

构建插件(产物自动输出到上面的插件目录):

```bash
bash scripts/build-plugins.sh
# 等价于: mvn -f plugins/pom.xml package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true
```

设置环境变量并启动 starter:

```bash
export JNIMBLE_DB_URL='jdbc:mysql://localhost:3306/jnimble?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&createDatabaseIfNotExist=true'
export JNIMBLE_DB_USERNAME=root
export JNIMBLE_DB_PASSWORD='<your-mysql-password>'
export JNIMBLE_DEFAULT_ADMIN_PASSWORD='<admin-password>'

cd jnimble-framework
mvn -pl jnimble-starter spring-boot:run
```

启动后访问 http://localhost:8080/admin,使用 `admin` / `JNIMBLE_DEFAULT_ADMIN_PASSWORD` 登录,业务插件出现在侧边栏。

开发期改插件:重新 `mvn -f plugins/pom.xml package`(或 `bash scripts/build-plugins.sh`),新 JAR 落盘后运行中的应用会**热部署**该插件,无需重启。

> 为什么不让 starter 依赖插件:那会让"平台侧依赖插件",方向反了,也会掩盖真实依赖(例如 license-issuer 插件需要框架模块 `jnimble-license-sdk`)。当前做法把耦合留在业务侧构建,依赖方向始终是"插件 → 平台"。

### 3. 生产部署

与开发同一机制:把插件 JAR 放进目标 starter 的 `data/plugins/` 即可,
`dev-classpath-enabled` 保持默认 `false`(目录加载 + 运行期热部署)。

```bash
bash scripts/build-plugins.sh            # 或 mvn -f plugins/pom.xml package
# 产物已在 jnimble-framework/jnimble-starter/data/plugins/,拷到生产 starter 的 data/plugins/ 即可
```

## 关键配置开关:`jnimble.plugins.dev-classpath-enabled`

控制 starter 是否从 **classpath** 自动发现并安装插件。

| 取值 | 行为 | 适用场景 |
|------|------|---------|
| `false`(默认) | 只从 `data/plugins/` 目录加载 JAR | 本仓库开发与生产统一使用 |
| `true` | 扫描 classpath 上的 `META-INF/jnimble-plugin.json` 并安装 | 需自行把插件置于运行时 classpath |

> 注意:`true` 需要插件类出现在**应用运行时 classpath** 上,即应用必须依赖插件,会破坏"平台不依赖插件"的方向约束。本仓库不使用该模式。

## 插件目录约定

```
jnimble-order-plugin/
├── jnimble-framework/                    # 本地框架 checkout(被 gitignore,由 setup-framework.sh 生成)
│   └── jnimble-starter/                  # 启动入口(starter 不依赖业务插件)
│       ├── pom.xml                       # 仅框架模块依赖
│       └── data/plugins/                 # 插件 JAR 目录(构建产物落盘于此)
└── plugins/
    ├── pom.xml                           # 聚合 + dependencyManagement 统一内部版本
    └── jnimble-plugin-*/                 # 各业务插件
        ├── pom.xml
        └── src/main/
            ├── java/.../XxxPluginBoot.java            # 实现 PluginBoot
            └── resources/
                ├── META-INF/jnimble-plugin.json       # 插件描述符(必需)
                ├── templates/plugin/{pluginId}/       # 模板路径必须 plugin/{pluginId}/ 开头
                ├── i18n/                               # 国际化
                └── db/migration/plugin/{pluginId}/    # 插件级 Flyway 迁移
```

## 开发约定

- 插件开发完整指南见 [`doc/plugin-development.md`](doc/plugin-development.md)(目录结构、描述符字段、扩展点、迁移、i18n、依赖、常见坑、完整示例)
- 插件模板路径必须以 `plugin/{pluginId}/` 开头
- 权限码必须以 `{pluginId}.` 开头,否则不会同步到角色管理
- 数据库变更只加新 `V{n}__*.sql` 脚本,不改旧脚本(Flyway checksum)
- 业务插件之间互引版本统一在 `plugins/pom.xml` 的 `<dependencyManagement>` 声明
- 完整规范参考上游 [CLAUDE.md](https://github.com/what123/JNimble/blob/main/CLAUDE.md)

## 上游仓库

- 框架源码:[what123/JNimble](https://github.com/what123/JNimble)
- 本仓库**只含插件**;框架不在此跟踪,由 `scripts/setup-framework.sh` 拉取到被忽略的 `jnimble-framework/` 供构建使用。
- 上游框架在本仓库的提交历史中**保持原状、未被修改**;业务插件不与框架源码耦合,依赖方向恒为"插件 → 平台"。

## 授权

Apache 2.0 + 商业授权双重模式。开源使用必须保留后台 UI 与登录页的版权信息;去版权需联系 `178277164@qq.com` 购买商业授权。详见 [NOTICE](NOTICE) 与 [LICENSE](LICENSE)。
