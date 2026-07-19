# jnimble-order-plugin

JNimble 业务插件集合 — 基于 [JNimble](https://github.com/what123/JNimble) 插件化后台框架开发,聚合了点餐、支付、打印、菜单、CRM、License 等多个业务插件。

仓库结构:

```
jnimble-order-plugin/
├── jnimble-framework/      # 主框架 submodule(what123/JNimble)
└── plugins/               # 业务插件聚合(pom)
    ├── jnimble-plugin-demo-crm
    ├── jnimble-plugin-license-issuer
    ├── jnimble-plugin-menu-manager
    ├── jnimble-plugin-order-core
    ├── jnimble-plugin-order-table
    ├── jnimble-plugin-payment
    ├── jnimble-plugin-printer-core
    └── jnimble-plugin-printer-feie
```

## 环境要求

- JDK 21+
- Maven 3.9+
- MySQL 8+(或任意 Spring Boot 支持的 DataSource)

## 快速开始

### 1. 克隆(带 submodule)

```bash
git clone --recurse-submodules <本仓库地址>
cd jnimble-order-plugin
```

如果已经 clone 但忘了带 `--recurse-submodules`:

```bash
git submodule update --init --recursive
```

### 2. 构建 & 本地启动(开发模式,推荐)

开发模式下,starter 会把 `plugins/` 下所有业务插件作为 classpath 依赖直接引入,**无需打 JAR、无需拷贝到 `data/plugins/`**,改完代码直接 `spring-boot:run` 即可看到效果。

> ⚠️ 关键步骤:framework submodule 的两处本地改动(starter pom 加业务插件依赖、application.yml 开 `dev-classpath-enabled`)不会进入 framework 上游仓库,clone 后需要执行一次补丁脚本应用这两处改动:
>
> ```bash
> bash scripts/apply-dev-classpath.sh
> ```
>
> 脚本幂等,已应用会直接返回。重新 `git submodule update` 升级 framework 后需再跑一次。

首次启动前,先安装 framework 到本地 Maven 仓库(因为 starter 依赖 framework 各模块):

```bash
cd jnimble-framework
mvn clean install -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true
cd ..
```

再安装业务插件到本地仓库(开发期 classpath 发现依赖本地仓库的 JAR):

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

> ⚠️ 本地改动:以上"clone 即可开发"能力依赖于对 `jnimble-framework/jnimble-starter` 的两处本地改动:
> - `jnimble-starter/pom.xml` 添加了对 `plugins/` 下各业务模块的 `<dependency>`(classpath 引入)
> - `jnimble-starter/src/main/resources/application.yml` 把 `jnimble.plugins.dev-classpath-enabled` 默认值改为 `true`
>
> 这两处改动**不会 push 到 `what123/JNimble` 上游仓库**,clone 本仓库后通过 `bash scripts/apply-dev-classpath.sh` 应用(脚本幂等)。开源用户跑一次脚本即可拥有此能力;若要把 framework 升级到新版本,重新 `git submodule update` 后需重新跑一次脚本(见 `doc/dev-classpath-setup.md`)。

### 3. 生产部署(打包成 JAR)

生产环境关闭 classpath 发现,改用插件目录热部署:

```bash
mvn -f plugins/pom.xml package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true

# 把各插件 target/*.jar 拷到生产 starter 的 data/plugins/
cp plugins/jnimble-plugin-*/target/jnimble-plugin-*.jar <生产 starter 目录>/data/plugins/
```

生产启动时设置环境变量 `JNIMBLE_DEV_CLASSPATH_ENABLED=false`(或不设,看你是否保留 application.yml 的默认值)。

## 关键配置开关:`jnimble.plugins.dev-classpath-enabled`

控制 starter 是否从 classpath 自动发现并安装插件。两个取值:

| 值 | 行为 | 适用场景 |
|----|------|---------|
| `true`(本仓库默认) | starter 启动时扫描 classpath 上所有 `META-INF/jnimble-plugin.json`,直接以 classpath 形式安装并 `boot()`。改代码后重启即生效,不需要打 JAR | 开发期 |
| `false`(framework 上游默认) | 关闭 classpath 发现,只从 `data/plugins/` 目录加载 JAR | 生产部署 |

设置方式:

- 环境变量:`JNIMBLE_DEV_CLASSPATH_ENABLED=true|false`(优先级最高)
- `application.yml`:`jnimble.plugins.dev-classpath-enabled: true|false`

**配套条件**:开 `dev-classpath-enabled=true` 时,starter 的 pom 必须把业务插件作为 `<dependency>` 引入,否则 classpath 上没有插件类,发现不到。本仓库的 `jnimble-framework/jnimble-starter/pom.xml` 已做好这一步。

## 目录结构

```
jnimble-order-plugin/
├── jnimble-framework/                    # 主框架 submodule
│   └── jnimble-starter/                  # 开发期启动入口
│       ├── pom.xml                       # 本地改动:依赖 plugins 各模块
│       └── src/main/resources/application.yml  # 本地改动:dev-classpath-enabled=true
└── plugins/
    ├── pom.xml                           # 业务插件聚合,dependencyManagement 统一内部版本
    └── jnimble-plugin-*/                 # 各业务插件
        ├── pom.xml
        └── src/main/
            ├── java/.../XxxPluginBoot.java            # 实现 PluginBoot
            └── resources/
                ├── META-INF/jnimble-plugin.json       # 插件描述符(必需)
                ├── templates/plugin/{pluginId}/        # 模板路径必须 plugin/{pluginId}/ 开头
                ├── i18n/                                # 国际化
                └── db/migration/plugin/{pluginId}/    # 插件级 Flyway 迁移
```

## 开发约定

- 插件模板路径必须以 `plugin/{pluginId}/` 开头
- 权限码必须以 `{pluginId}.` 开头,否则不会同步到角色管理
- 数据库变更只加新 `V{n}__*.sql`,不改旧脚本(Flyway checksum)
- 业务插件之间互引版本统一在 `plugins/pom.xml` 的 `<dependencyManagement>` 声明
- 详细规范参考 [主框架 CLAUDE.md](https://github.com/what123/JNimble/blob/main/CLAUDE.md)

## 授权

Apache 2.0 + 商业授权双重模式。保留版权可商用,去版权需联系 `178277164@qq.com`。详见 [NOTICE](NOTICE)。
