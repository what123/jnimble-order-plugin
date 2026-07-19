# jnimble-order-plugin

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-21-blue.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/maven-3.9+-blue.svg)](https://maven.apache.org/)
[![Based on JNimble](https://img.shields.io/badge/based%20on-JNimble-blue.svg)](https://github.com/what123/JNimble)

[English](README.md) | 中文

> **基于 [JNimble](https://github.com/what123/JNimble) 开发** —— JNimble 是一个插件化的 Java 后台管理框架。本仓库在其之上开发了若干业务插件(点餐、支付、打印、菜单、CRM、License 等),通过 git submodule 的方式把框架源码引入到 `jnimble-framework/` 目录,固定指向 `what123/JNimble` 的某个 commit。

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
├── jnimble-framework/      # 上游框架 submodule(what123/JNimble,固定 commit)
└── plugins/               # 业务插件聚合(pom)
    ├── pom.xml            # parent -> ../jnimble-framework,dependencyManagement 统一内部版本
    └── jnimble-plugin-*/  # 8 个业务插件
```

框架以 submodule 引入,便于插件编译期直接引用本地框架源码、支持两侧联调。**不修改上游框架源码**;为了让"clone 即可开发"成立,framework submodule 有两处本地 working-tree 补丁(见 [开发模式:clone 即跑](#开发模式clone-即跑)),不会进入上游仓库的提交历史。

## 环境要求

- JDK 21+
- Maven 3.9+
- MySQL 8+(或任意 Spring Boot 支持的 DataSource)

## 快速开始

### 1. 克隆(带 submodule)

```bash
git clone --recurse-submodules https://github.com/what123/jnimble-order-plugin.git
cd jnimble-order-plugin
```

如果已经 clone 但忘了带 `--recurse-submodules`:

```bash
git submodule update --init --recursive
```

### 2. 构建 & 本地启动(开发模式,推荐)

开发模式下,starter 会把 `plugins/` 下所有业务插件作为 classpath 依赖直接引入,**无需打 JAR、无需拷贝到 `data/plugins/`**,改完代码直接 `spring-boot:run` 即可看到效果。

> ⚠️ **一次性补丁步骤**:framework submodule 需要两处本地改动(starter pom 加业务插件依赖、application.yml 开 `dev-classpath-enabled`)才能开启 clone-and-run。这两处改动**有意不推送**到 `what123/JNimble` 上游,clone 后通过下面脚本应用到本地 working tree:
>
> ```bash
> bash scripts/apply-dev-classpath.sh
> ```
>
> 脚本幂等,已应用的工作树会直接返回。每次 `git submodule update` 升级 framework 后需重新跑一次。详见 [`doc/dev-classpath-setup.md`](doc/dev-classpath-setup.md)。

首次启动前,先安装 framework 到本地 Maven 仓库(starter 依赖 framework 各模块):

```bash
cd jnimble-framework
mvn clean install -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true
cd ..
```

再安装业务插件到本地仓库(开发期 classpath 发现依赖 `~/.m2` 中的 JAR):

```bash
mvn -f plugins/pom.xml clean install -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true
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

启动后访问 http://localhost:8080/admin,使用 `admin` / `JNIMBLE_DEFAULT_ADMIN_PASSWORD` 登录,业务插件会自动出现在侧边栏。

> ⚠️ "clone 即可开发"能力依赖于 `jnimble-framework/jnimble-starter` 的两处本地改动:
> - `jnimble-starter/pom.xml` 为每个业务插件模块添加 `<dependency>`(classpath 引入)
> - `jnimble-starter/src/main/resources/application.yml` 把 `jnimble.plugins.dev-classpath-enabled` 默认值从 `false` 改为 `true`
>
> 这两处改动**不会 push 到 `what123/JNimble` 上游仓库**。clone 本仓库后通过 `bash scripts/apply-dev-classpath.sh` 应用(脚本幂等)。每次 `git submodule update` 升级 framework 后需重新跑一次脚本(见 [`doc/dev-classpath-setup.md`](doc/dev-classpath-setup.md))。

### 3. 生产部署(打包成 JAR)

生产环境关闭 classpath 发现,改用插件目录热部署:

```bash
mvn -f plugins/pom.xml package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true

# 把各插件 target/*.jar 拷到生产 starter 的 data/plugins/
cp plugins/jnimble-plugin-*/target/jnimble-plugin-*.jar <生产 starter 目录>/data/plugins/
```

生产启动时设置环境变量 `JNIMBLE_DEV_CLASSPATH_ENABLED=false`(或不设,视 application.yml 的默认值)。

## 关键配置开关:`jnimble.plugins.dev-classpath-enabled`

控制 starter 是否从 classpath 自动发现并安装插件。

| 取值 | 行为 | 适用场景 |
|------|------|---------|
| `true`(本仓库默认) | starter 启动时扫描 classpath 上所有 `META-INF/jnimble-plugin.json`,直接以 classpath 形式安装并 `boot()`。改代码后重启即生效,不需要打 JAR | 开发期 |
| `false`(framework 上游默认) | 关闭 classpath 发现,只从 `data/plugins/` 目录加载 JAR | 生产部署 |

设置方式:

- 环境变量:`JNIMBLE_DEV_CLASSPATH_ENABLED=true|false`(优先级最高)
- `application.yml`:`jnimble.plugins.dev-classpath-enabled: true|false`

**配套条件**:开 `dev-classpath-enabled=true` 时,starter 的 pom 必须把业务插件作为 `<dependency>` 引入,否则 classpath 上没有插件类,发现不到。`jnimble-framework/jnimble-starter/pom.xml` 已通过补丁脚本完成这一步。

## 插件目录约定

```
jnimble-order-plugin/
├── jnimble-framework/                    # 上游框架 submodule
│   └── jnimble-starter/                  # 开发期启动入口
│       ├── pom.xml                       # 本地改动:依赖 plugins/*
│       └── src/main/resources/application.yml  # 本地改动:dev-classpath-enabled=true
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

- 插件模板路径必须以 `plugin/{pluginId}/` 开头
- 权限码必须以 `{pluginId}.` 开头,否则不会同步到角色管理
- 数据库变更只加新 `V{n}__*.sql` 脚本,不改旧脚本(Flyway checksum)
- 业务插件之间互引版本统一在 `plugins/pom.xml` 的 `<dependencyManagement>` 声明
- 完整规范参考上游 [CLAUDE.md](https://github.com/what123/JNimble/blob/main/CLAUDE.md)

## 上游仓库

- 框架源码:[what123/JNimble](https://github.com/what123/JNimble)
- 本仓库通过 `jnimble-framework/` submodule 固定指向 `what123/JNimble` 的某个 commit,运行 `git submodule status` 可查看固定版本。
- 上游框架在本仓库的提交历史中**保持原状、未被修改**;开启开发模式所需的两处 working-tree 补丁通过 [`scripts/apply-dev-classpath.sh`](scripts/apply-dev-classpath.sh) 本地应用,不会推送到上游。

## 授权

Apache 2.0 + 商业授权双重模式。开源使用必须保留后台 UI 与登录页的版权信息;去版权需联系 `178277164@qq.com` 购买商业授权。详见 [NOTICE](NOTICE) 与 [LICENSE](LICENSE)。
