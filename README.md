# jnimble-order-plugin

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-21-blue.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/maven-3.9+-blue.svg)](https://maven.apache.org/)
[![Based on JNimble](https://img.shields.io/badge/based%20on-JNimble-blue.svg)](https://github.com/what123/JNimble)

English | [中文](README_CN.md)

> **Built on [JNimble](https://github.com/what123/JNimble)** — a plugin-driven Java admin framework. This repository ships a set of business plugins (ordering, payment, printing, menu, CRM, license issuer, etc.) developed on top of JNimble. The framework is fetched locally into `jnimble-framework/` by `scripts/setup-framework.sh` (gitignored — this repo contains plugins only).

## Overview

`jnimble-order-plugin` is a multi-plugin Maven reactor that ships vertical business capabilities for the JNimble admin framework. Instead of modifying the framework source, every feature is delivered as an independent plugin JAR — install to use, uninstall to remove.

Bundled plugins:

| Plugin | Description |
|--------|-------------|
| `jnimble-plugin-order-core` | Order management core (kitchen queue, order state machine) |
| `jnimble-plugin-order-table` | Table-based ordering (table map, seat routing) |
| `jnimble-plugin-payment` | Payment aggregation and reconciliation |
| `jnimble-plugin-printer-core` | Printer abstraction and template registry |
| `jnimble-plugin-printer-feie` | Feie (飞鹅) cloud printer driver |
| `jnimble-plugin-menu-manager` | Menu / product / spec management |
| `jnimble-plugin-demo-crm` | Demo CRM plugin (reference for hook/route/asset registration) |
| `jnimble-plugin-license-issuer` | License issuer UI + key management (admin side of `jnimble-license-sdk`) |

## Repository Layout

```
jnimble-order-plugin/
├── plugins/               # business plugin aggregation (pom)
│   ├── pom.xml            # parent -> ../jnimble-framework, dependencyManagement for inter-plugin versions
│   └── jnimble-plugin-*/  # business plugins
├── scripts/               # setup-framework.sh, build-plugins.sh
├── doc/                   # plugin development guide & docs
└── jnimble-framework/     # local framework checkout (gitignored; created by scripts/setup-framework.sh)
```

This repo contains **only plugin code**. For development, `scripts/setup-framework.sh` clones the framework into a gitignored `jnimble-framework/` directory and installs it to your local Maven repository, so plugins can compile against it.

## Requirements

- JDK 21+
- Maven 3.9+
- MySQL 8+ (or any Spring Boot-supported DataSource)

## Quick Start

### 1. Clone

```bash
git clone https://github.com/what123/jnimble-order-plugin.git
cd jnimble-order-plugin
```

### 2. Build & Run (Development Mode, recommended)

The starter does **not** depend on any business plugin — its classpath stays clean. Plugins are installed as JARs from
`jnimble-framework/jnimble-starter/data/plugins/`, loaded by the starter's `PluginDirectoryInitializer` (startup scan)
and `PluginDirectoryWatcher` (runtime hot-deploy). The dependency direction is always *plugin → platform*.
See [`doc/dev-workflow.md`](doc/dev-workflow.md).

Fetch & install the framework to local Maven (both the starter and the plugin builds depend on it):

```bash
bash scripts/setup-framework.sh
# clones what123/JNimble into ./jnimble-framework (gitignored) and runs mvn install
```

Build the plugins (artifacts are written to the plugin directory above):

```bash
bash scripts/build-plugins.sh
# equivalent to: mvn -f plugins/pom.xml package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true
```

Set env vars and start the starter:

```bash
export JNIMBLE_DB_URL='jdbc:mysql://localhost:3306/jnimble?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&createDatabaseIfNotExist=true'
export JNIMBLE_DB_USERNAME=root
export JNIMBLE_DB_PASSWORD='<your-mysql-password>'
export JNIMBLE_DEFAULT_ADMIN_PASSWORD='<admin-password>'

cd jnimble-framework
mvn -pl jnimble-starter spring-boot:run
```

Visit http://localhost:8080/admin, sign in as `admin` / the `JNIMBLE_DEFAULT_ADMIN_PASSWORD` you set. Business plugins appear in the sidebar.

To change a plugin during development, run `mvn -f plugins/pom.xml package` (or `bash scripts/build-plugins.sh`) again — the new JAR lands in `data/plugins/` and the running app **hot-deploys** it, no restart needed.

> Why not let the starter depend on plugins: that makes the *platform* depend on plugins (wrong direction) and masks
> real dependencies (e.g. the license-issuer plugin needs the framework module `jnimble-license-sdk`). Keeping the
> coupling in the business-side build preserves the *plugin → platform* direction.

### 3. Production Deployment

Same mechanism as development: drop the plugin JARs into the target starter's `data/plugins/`; keep
`dev-classpath-enabled` at its default `false` (directory load + runtime hot-deploy).

```bash
bash scripts/build-plugins.sh            # or mvn -f plugins/pom.xml package
# artifacts are already in jnimble-framework/jnimble-starter/data/plugins/; copy them to the production starter's data/plugins/
```

## Configuration Switch: `jnimble.plugins.dev-classpath-enabled`

Controls whether the starter auto-discovers and installs plugins from its **classpath**.

| Value | Behavior | Use Case |
|-------|----------|----------|
| `false` (default) | Load JARs only from the `data/plugins/` directory | Used for both dev and prod in this repo |
| `true` | Scan `META-INF/jnimble-plugin.json` on the classpath and install | Only when you place plugins on the runtime classpath yourself |

> Note: `true` requires plugin classes to be on the **application runtime classpath**, i.e. the app must depend on the
> plugins — which breaks the "platform must not depend on plugins" direction. This repo does not use it.

## Plugin Layout Convention

```
jnimble-order-plugin/
├── jnimble-framework/                    # local framework checkout (gitignored; created by setup-framework.sh)
│   └── jnimble-starter/                  # launch entry (starter depends on no business plugin)
│       ├── pom.xml                       # framework modules only
│       └── data/plugins/                 # plugin JAR directory (build output lands here)
└── plugins/
    ├── pom.xml                           # aggregation + dependencyManagement for inter-plugin versions
    └── jnimble-plugin-*/                # each business plugin
        ├── pom.xml
        └── src/main/
            ├── java/.../XxxPluginBoot.java            # implements PluginBoot
            └── resources/
                ├── META-INF/jnimble-plugin.json       # descriptor (required)
                ├── templates/plugin/{pluginId}/       # template paths MUST start with plugin/{pluginId}/
                ├── i18n/                               # internationalization
                └── db/migration/plugin/{pluginId}/    # plugin-scoped Flyway migrations
```

## Development Conventions

- Plugin development guide: [`doc/plugin-development.md`](doc/plugin-development.md) (layout, descriptor fields, extension points, migrations, i18n, dependencies, pitfalls, full example)
- Plugin template paths MUST start with `plugin/{pluginId}/`
- Permission codes MUST start with `{pluginId}.` — otherwise they won't sync to role management
- For DB changes, only add new `V{n}__*.sql` scripts; never edit existing ones (Flyway checksum)
- Inter-plugin version references are centralized in `plugins/pom.xml` `<dependencyManagement>`
- See the upstream [CLAUDE.md](https://github.com/what123/JNimble/blob/main/CLAUDE.md) for full conventions

## Upstream

- Framework source: [what123/JNimble](https://github.com/what123/JNimble)
- This repository contains **plugins only** — the framework is not vendored or tracked here. `scripts/setup-framework.sh`
  fetches it into a gitignored `jnimble-framework/` directory for building.
- Business plugins are not coupled into framework source — the dependency direction is always *plugin → platform*.

## License

Apache 2.0 + Commercial dual-license. Open-source use must retain the copyright footer
in the admin UI and login page. Copyright removal requires a commercial license from
`178277164@qq.com`. See [NOTICE](NOTICE) and [LICENSE](LICENSE) for full terms.
