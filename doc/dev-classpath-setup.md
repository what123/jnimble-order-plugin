# 开发模式 classpath 插件发现配置说明

## 背景

JNimble 主框架默认通过 `data/plugins/` 目录加载插件 JAR 来运行(`PluginDirectoryWatcher` 热部署)。开发期这种模式不够便利:每次改完代码都要 `mvn package` + 拷 JAR 才能看到效果。

framework 提供了一个开关 `jnimble.plugins.dev-classpath-enabled`,开启后 starter 会扫描 classpath 上的 `META-INF/jnimble-plugin.json`,直接以 classpath 形式安装插件,免去打 JAR 步骤。本仓库为了让"clone 即可开发",在本地 working tree 启用了此开关。

## 两处本地改动(不会 push 到 `what123/JNimble` 上游)

### 1. `jnimble-framework/jnimble-starter/pom.xml`

在 `<dependencies>` 末尾追加了对 `plugins/` 下所有业务插件的依赖,把插件类引入 starter 的 classpath:

```xml
<dependency>
    <groupId>com.jnimble</groupId>
    <artifactId>jnimble-plugin-order-core</artifactId>
    <version>${project.version}</version>
</dependency>
<!-- ...其余 7 个业务插件同上... -->
```

### 2. `jnimble-framework/jnimble-starter/src/main/resources/application.yml`

```yaml
jnimble:
  plugins:
    dev-classpath-enabled: ${JNIMBLE_DEV_CLASSPATH_ENABLED:true}   # 上游默认 false,本仓库改为 true
```

## 重新应用

当 `jnimble-framework` submodule 升级到新版本(`git submodule update`)后,以上两处本地改动会被覆盖,需重新应用:

```bash
bash scripts/apply-dev-classpath.sh
```

脚本基于 `scripts/dev-classpath.patch`(由 `git diff` 导出的 patch 文件),幂等执行:已应用直接返回,未应用则应用补丁。如果 framework 上游的对应代码块已发生显著变化导致 `git apply` 失败,脚本会报错,此时需手动按下面的步骤应用。

### 手动应用步骤(脚本失败时)

1. 重新在 `jnimble-starter/pom.xml` 追加 8 个业务插件 `<dependency>`(参考下面的片段,artifactId 列表见 `plugins/pom.xml` 的 `<modules>`)。
2. 重新把 `application.yml` 的 `dev-classpath-enabled` 默认值改成 `true`(或仅通过环境变量 `JNIMBLE_DEV_CLASSPATH_ENABLED=true` 设置,不改文件)。

## 关闭

如需临时关闭 classpath 发现、改回 JAR 部署模式,用环境变量覆盖即可(不需要改文件):

```bash
export JNIMBLE_DEV_CLASSPATH_ENABLED=false
mvn -pl jnimble-starter spring-boot:run
```

然后按生产部署流程把 JAR 拷到 `jnimble-starter/data/plugins/`。

## 相关数据库表

classpath 发现的插件会写入 `jnimble_plugin_state` 表,`source` 列为 `CLASSPATH`。JAR 部署的插件 `source` 为 `JAR` 并记录 `artifact_path`。混用时优先以 DB 记录为准,如果切换模式后出现加载异常,可清空或禁用旧记录:

```sql
-- 查看状态
SELECT plugin_id, source, status, enabled, artifact_path FROM jnimble_plugin_state;

-- 禁用所有指向旧绝对路径的 JAR 记录(避免加载到失效的旧 JAR)
UPDATE jnimble_plugin_state
SET enabled = 0, status = 'INSTALLED', artifact_path = NULL
WHERE source = 'JAR' OR artifact_path IS NOT NULL;
```
