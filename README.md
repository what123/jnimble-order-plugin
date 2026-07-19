# jnimble-order-plugin

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-21-blue.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/maven-3.9+-blue.svg)](https://maven.apache.org/)
[![Based on JNimble](https://img.shields.io/badge/based%20on-JNimble-blue.svg)](https://github.com/what123/JNimble)

English | [中文](README_CN.md)

> **Built on [JNimble](https://github.com/what123/JNimble)** — a plugin-driven Java admin framework. This repository aggregates a set of business plugins (ordering, payment, printing, menu, CRM, license issuer, etc.) developed on top of JNimble. The framework is referenced as a git submodule under `jnimble-framework/`, pinned to a specific commit of `what123/JNimble`.

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
├── jnimble-framework/      # upstream framework submodule (what123/JNimble, pinned commit)
└── plugins/               # business plugin aggregation (pom)
    ├── pom.xml            # parent -> ../jnimble-framework, dependencyManagement for inter-plugin versions
    └── jnimble-plugin-*/  # 8 business plugins
```

The framework is referenced as a submodule so the plugins can compile against local framework source — convenient for two-sided debugging. Upstream framework source is **not modified** in this repo's history; two local working-tree patches (see [Development Mode](#development-mode-clone--run)) enable "clone and run" without touching upstream.

## Requirements

- JDK 21+
- Maven 3.9+
- MySQL 8+ (or any Spring Boot-supported DataSource)

## Quick Start

### 1. Clone (with submodule)

```bash
git clone --recurse-submodules https://github.com/what123/jnimble-order-plugin.git
cd jnimble-order-plugin
```

If you forgot `--recurse-submodules`:

```bash
git submodule update --init --recursive
```

### 2. Build & Run (Development Mode, recommended)

In dev mode, the starter pulls every plugin under `plugins/` onto its classpath. **No JAR packaging, no copying to `data/plugins/`** — just `spring-boot:run` after editing code.

> ⚠️ **One-time patch step**: two local working-tree changes (starter pom deps + `dev-classpath-enabled=true`) are required in the framework submodule to enable clone-and-run. They are intentionally **not pushed** to `what123/JNimble` upstream, so apply them locally after clone:
>
> ```bash
> bash scripts/apply-dev-classpath.sh
> ```
>
> Idempotent — re-running on an already-patched tree is a no-op. Re-run once after each `git submodule update`. See [`doc/dev-classpath-setup.md`](doc/dev-classpath-setup.md) for details.

Install the framework to local Maven (starter depends on its modules):

```bash
cd jnimble-framework
mvn clean install -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true
cd ..
```

Install business plugins to local Maven (dev classpath discovery reads from `~/.m2`):

```bash
mvn -f plugins/pom.xml clean install -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true
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

Visit http://localhost:8080/admin, sign in as `admin` / the `JNIMBLE_DEFAULT_ADMIN_PASSWORD` you set. Business plugins appear in the sidebar automatically.

> ⚠️ The clone-and-run capability depends on two local changes in `jnimble-framework/jnimble-starter`:
> - `jnimble-starter/pom.xml` adds `<dependency>` entries for every business plugin module (classpath inclusion)
> - `jnimble-starter/src/main/resources/application.yml` changes `jnimble.plugins.dev-classpath-enabled` default from `false` to `true`
>
> These changes **are not pushed to `what123/JNimble` upstream**. After cloning this repo, apply them via `bash scripts/apply-dev-classpath.sh` (idempotent). Re-run the script after each `git submodule update`. See [`doc/dev-classpath-setup.md`](doc/dev-classpath-setup.md).

### 3. Production Deployment (JAR-based)

For production, disable classpath discovery and use the plugin directory hot-deploy:

```bash
mvn -f plugins/pom.xml package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true

# Copy each plugin JAR to the production starter's data/plugins/
cp plugins/jnimble-plugin-*/target/jnimble-plugin-*.jar <production-starter-dir>/data/plugins/
```

Set `JNIMBLE_DEV_CLASSPATH_ENABLED=false` (or leave the application.yml default) when starting in production.

## Configuration Switch: `jnimble.plugins.dev-classpath-enabled`

Controls whether the starter auto-discovers and installs plugins from its classpath.

| Value | Behavior | Use Case |
|-------|----------|----------|
| `true` (this repo's default) | Starter scans `META-INF/jnimble-plugin.json` on classpath, installs and `boot()`s each plugin directly. Code edits take effect on restart, no JAR build needed. | Development |
| `false` (upstream default) | Classpath discovery off; plugins only load from `data/plugins/` directory. | Production |

How to set:

- Env var: `JNIMBLE_DEV_CLASSPATH_ENABLED=true|false` (highest priority)
- `application.yml`: `jnimble.plugins.dev-classpath-enabled: true|false`

**Prerequisite**: when `dev-classpath-enabled=true`, the starter's pom must declare each business plugin as a `<dependency>` so its classes are on the classpath. `jnimble-framework/jnimble-starter/pom.xml` already does this (applied via the patch script).

## Plugin Layout Convention

```
jnimble-order-plugin/
├── jnimble-framework/                    # upstream framework submodule
│   └── jnimble-starter/                  # dev-time launch entry
│       ├── pom.xml                       # LOCAL CHANGE: depends on plugins/*
│       └── src/main/resources/application.yml  # LOCAL CHANGE: dev-classpath-enabled=true
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

- Plugin template paths MUST start with `plugin/{pluginId}/`
- Permission codes MUST start with `{pluginId}.` — otherwise they won't sync to role management
- For DB changes, only add new `V{n}__*.sql` scripts; never edit existing ones (Flyway checksum)
- Inter-plugin version references are centralized in `plugins/pom.xml` `<dependencyManagement>`
- See the upstream [CLAUDE.md](https://github.com/what123/JNimble/blob/main/CLAUDE.md) for full conventions

## Upstream

- Framework source: [what123/JNimble](https://github.com/what123/JNimble)
- This repository tracks a specific commit of `what123/JNimble` via the `jnimble-framework/` submodule.
  Run `git submodule status` to see the pinned commit.
- Upstream framework is intentionally left unmodified in this repo's commit history; the two
  working-tree patches enabling dev-classpath mode are applied locally via
  [`scripts/apply-dev-classpath.sh`](scripts/apply-dev-classpath.sh) and never pushed upstream.

## License

Apache 2.0 + Commercial dual-license. Open-source use must retain the copyright footer
in the admin UI and login page. Copyright removal requires a commercial license from
`178277164@qq.com`. See [NOTICE](NOTICE) and [LICENSE](LICENSE) for full terms.
