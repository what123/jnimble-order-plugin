# 开发流程:JAR 目录模式

## 背景

早期为了让"clone 即可运行",本仓库给框架 submodule 打了两处本地补丁:在
`jnimble-starter/pom.xml` 里把业务插件作为 classpath 依赖引入,并打开
`jnimble.plugins.dev-classpath-enabled`。这样虽然能开发,但引入了两个问题:

1. **依赖方向被破坏**:`jnimble-starter`(平台侧)反向依赖了业务插件,而约束是
   "插件可以依赖平台,平台不能依赖插件"。插件包里没有框架模块,平台一旦依赖插件,
   插件集就被写死在框架里。
2. **掩盖真实依赖**:插件对框架模块的传递依赖被"starter 直接依赖插件"顺带带上,
   例如 `jnimble-license-sdk`。生产以 JAR 方式部署时会突然 `NoClassDefFoundError`。

现在改为**统一的 JAR 目录模式**,starter 不依赖任何业务插件。

## 机制

- 插件以 JAR 形式放在 `jnimble-framework/jnimble-starter/data/plugins/`。
- 启动时 `PluginDirectoryInitializer` 扫描该目录,按描述符 `dependencies` 的依赖顺序
  自动安装并启用(`jnimble.plugins.auto-enable=true`)。
- 运行期 `PluginDirectoryWatcher` 监听目录:新 JAR → 安装;内容变化 → 热替换
  (带回滚)。**无需重启**。
- `dev-classpath-enabled` 保持框架默认 `false`。

## 一次性准备

```bash
# 1. 拉取 submodule
git submodule update --init --recursive

# 2. 安装框架到本地 Maven 仓库(插件编译依赖它)
cd jnimble-framework
mvn clean install -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true
cd ..
```

## 开发循环

```bash
# 构建插件:产物自动输出到 jnimble-framework/jnimble-starter/data/plugins/
bash scripts/build-plugins.sh
# 等价于: mvn -f plugins/pom.xml package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true

# 启动(starter 从 data/plugins/ 加载插件)
cd jnimble-framework
mvn -pl jnimble-starter spring-boot:run
```

改动某个插件后,再次 `mvn -f plugins/pom.xml package`(或只 `-pl` 该插件),新 JAR 落盘后
运行中的应用会**热部署**该插件;若应用未运行,下次启动自动加载。首次构建尚未落盘时,
`build-plugins.sh` 会打印输出目录与文件清单以便确认。

覆盖输出目录:`mvn -f plugins/pom.xml package -Dplugin.deploy.dir=/abs/path`。

## 关键配置

| 开关 | 值 | 说明 |
|------|----|------|
| `jnimble.plugins.dir` | `./data/plugins` | 插件目录(相对 `spring-boot:run` 工作目录即 `jnimble-starter/`) |
| `jnimble.plugins.directory-scan-enabled` | `true` | 启动扫描目录并安装 |
| `jnimble.plugins.directory-watch-enabled` | `true` | 运行期监听目录热部署 |
| `jnimble.plugins.auto-enable` | `true` | 安装后自动启用 |
| `jnimble.plugins.dev-classpath-enabled` | `false` | 关闭 classpath 发现(保持框架默认) |

## 为什么不再用 classpath 模式

classpath 模式要求插件出现在**应用运行时 classpath** 上,唯一省事的办法就是让 starter
依赖插件,从而破坏依赖方向并掩盖传递依赖。JAR 目录模式让插件与平台彻底解耦:
starter 的 classpath 上没有任何业务插件类,依赖方向始终是"插件 → 平台"。

## 插件间依赖

插件之间的依赖(order-table→order-core、payment→order-core、printer-feie→printer-core、
scan-consumer→order-table/menu-manager/order-core 等)在插件描述符的 `dependencies` 字段声明,
运行时由 `DependencyAwarePluginClassLoader` 装配,与"平台 ↔ 插件"的方向约束无关。

## 部署

生产部署与开发使用同一机制:把插件 JAR 放到目标 starter 的 `data/plugins/` 即可,
`dev-classpath-enabled` 用默认 `false`。
