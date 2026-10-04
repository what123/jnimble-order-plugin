# 框架侧变更(已同步到 `what123/JNimble`)

本目录记录开发 `jnimble-order-plugin` 时需要的**框架改动**。

## 现状(以 JNimble `main` 为准)

已推送到 `what123/JNimble`:

1. `docs/blueprints/ordering-system.md` —— 点餐系统组装指南(框架 + 插件下载/组装)。
2. `jnimble-platform/.../auth/JNimbleSecurityConfiguration.java` —— 匿名放行 logo 端点(`/admin/system-settings/logo/**`) + CORS + 忽略 `/api,/plugins` 的 CSRF。

以上改动已应用并推送;`framework-changes.patch` 是相对 pin 基线(`9b92154`)的补丁,仅供查阅/重新应用。

## 关于 License(已放弃:框架保持中立)

授权**不放进框架**,由**插件自己**处理(自停用,或只拦截使用),签发端私有、单独维护。因此:

- 曾经加入的 `jnimble-license-vendor/`(vendored `jnimble-license-sdk` 二进制)、starter 的 `jnimble-license-sdk` 依赖、`V5__init_plugin_license.sql` 已**回退**(见 JNimble 的 `Revert "feat(license): ..."` 提交)。
- 框架 README 的生态表、本仓库 README 的插件表都已**移除 `license-issuer`**。
- 之后的 `framework-license-changes.patch` 已删除。

## ⚠️ 安全评审提醒

`JNimbleSecurityConfiguration.java` 的改动包含:

- **logo 放行**:`/admin/system-settings/logo/**` 匿名可访问(登录页展示站点 logo 需要)。
- **CORS**:`allowedOriginPatterns("*")` 且 `allowCredentials(true)` —— 生产应收敛为白名单。
- **CSRF**:对 `/api/**`、`/plugins/**` 关闭 —— 确认这些端点另有 CSRF 防护。

建议把 CORS/CSRF 做成可配置项再合并上游。
