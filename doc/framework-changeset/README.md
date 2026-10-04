# 框架侧变更集(提交到 `what123/JNimble`)

把 `jnimble-order-plugin` 开发/运行所需的**框架改动**整理成补丁,便于同步到 `what123/JNimble`。
补丁以 JNimble 的 pin commit(`9b921548b8b0f7f435b5deae1b324e78f32d6fe0`)为基线生成,
并已在干净的上游树上验证可 `git apply`(license 补丁含二进制,用 `--binary`)。

## 两个补丁(按"是否含 license"拆分)

### 1. `framework-changes.patch` —— **本次可提交(不含 license)**

| 文件 | 类型 | 说明 |
|---|---|---|
| `docs/blueprints/ordering-system.md` | 新增 | 点餐系统组装指南(框架 + 插件下载/组装) |
| `jnimble-platform/.../auth/JNimbleSecurityConfiguration.java` | 修改 | 匿名放行 logo 端点 + CORS + 忽略 `/api,/plugins` 的 CSRF |

### 2. `framework-license-changes.patch` —— **license 相关(随框架分发的二进制)**

| 文件 | 类型 | 说明 |
|---|---|---|
| `jnimble-license-vendor/` | **新增模块** | 携带预构建的 `jnimble-license-sdk` 二进制(jar + pom) |
| `jnimble-license-vendor/pom.xml` | 新增 | 构建时用 `maven-install-plugin:install-file` 把该 jar 装入本地仓库 |
| `pom.xml`(根) | 修改 | `<modules>` 首位加入 `jnimble-license-vendor`(保证先于 starter 构建) |
| `jnimble-starter/pom.xml` | 修改 | 依赖 `com.jnimble:jnimble-license-sdk` |
| `jnimble-starter/.../V5__init_plugin_license.sql` | 新增 | license 系统表迁移 |

## License 的分发设计(二进制随框架,源码私有)

**目标**:让任何拿到框架的人都能**开发 License 相关插件**(编译期需要 SDK),但**不公开 SDK 源码**。

**机制**:
1. SDK 源码放在**私有仓库** `JNimble-license`(不公开),在那里构建出 `jnimble-license-sdk-<版本>.jar`。
2. 把该 jar(及其 pom)提交进公开框架仓库的 `jnimble-license-vendor/` 模块。
3. 框架构建时,`jnimble-license-vendor` 用 `install-file` 把 jar 装入本地 Maven 仓库。
4. 之后 `jnimble-starter` 与**任何 License 插件**(如 `license-issuer`)即可正常
   `依赖 com.jnimble:jnimble-license-sdk:<版本>` —— 拿到 API 编译,但看不到源码。

**验证**:已实测 `mvn -f jnimble-framework install` 完整通过,`jnimble-license-vendor` 最先构建、
`jnimble-starter` 正常解析 `license-sdk`;插件仓库(含 `license-issuer`)构建也通过。

**更新 SDK 时**:在 `JNimble-license` 重新构建 → 用新 jar/pom 覆盖 `jnimble-license-vendor/` 下的文件,
若是版本升级同时更新文件名、`jnimble-license-vendor/pom.xml` 与 starter 依赖的版本号。

> 说明:
> - 该方式把**二进制**提交进框架 git(框架体积变大、升级需替换文件),换来"源码不公开 + 公众开箱可用"。
> - 若将来 SDK 有可公开的**稳定 API 层**,更推荐把 API 层开源、实现层保留为二进制,便于插件开发者使用。
> - 反编译防护不在此方案讨论范围(经确认暂不考虑);真正的授权保护来自**签发私钥保密 + machine code 绑定**。

## 应用方式

```bash
# 本次先提交非 license 部分
git apply -3 /path/to/framework-changes.patch

# 需要 license 能力时,再应用(含二进制)
git apply --binary -3 /path/to/framework-license-changes.patch
```

## ⚠️ 安全评审提醒(`framework-changes.patch` 内)

`JNimbleSecurityConfiguration.java` 的改动包含:

- **logo 放行**:`/admin/system-settings/logo/**` 匿名可访问(登录页展示站点 logo 需要;也修复"登录后跳到受保护 logo 端点 404")。
- **CORS**:`allowedOriginPatterns("*")` 且 `allowCredentials(true)` —— 允许任意来源携带凭据,生产应收敛为白名单。
- **CSRF**:对 `/api/**`、`/plugins/**` 关闭 —— 确认这些端点另有 CSRF 防护。

建议把 CORS/CSRF 做成**可配置项**再合并上游。

## 建议的提交拆分(一提交一关注点)

```
1) docs: add docs/blueprints/ordering-system.md
2) fix(security): permit anonymous GET /admin/system-settings/logo/** for login page
3) (另有评审) chore(security): configurable CORS + CSRF ignoring for /api,/plugins
4) feat(framework): ship prebuilt jnimble-license-sdk as vendored binary + V5__init_plugin_license migration
```
