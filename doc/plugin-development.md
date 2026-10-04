# 插件开发指南

> 适用范围:本仓库(`jnimble-order-plugin`)基于 JNimble 框架开发业务插件。
> 完整框架规范见上游 [CLAUDE.md](https://github.com/what123/JNimble/blob/main/CLAUDE.md),本文聚焦"在本仓库怎么开发一个插件并跑起来"。

## 0. 核心原则

1. **业务永远以插件形式交付**:不修改框架源码(`jnimble-kernel` / `jnimble-platform` / `jnimble-admin-shell`)来加业务功能。框架源码只在下述情况才动:补齐框架级缺口、修 bug。
2. **依赖方向不可逆:插件 → 平台**。插件可以 import `com.jnimble.sdk.*` 和 `com.jnimble.platform.*`;框架侧**禁止** import `com.jnimble.plugin.*` 或依赖任何业务插件。
   - 运行时由类加载器保证:插件包 `com.jnimble.plugin.*` 为 plugin-first(各插件隔离),`com.jnimble.sdk/kernel/platform` 为 parent-first(均由宿主提供)。
3. **包命名要独立**:每个插件使用互不包含的独立包路径(见 §4.3),否则插件自己的组件扫描会误扫到兄弟插件。
4. **权限码必须以 `{pluginId}.` 开头**,否则不会同步到角色管理。
5. **数据库变更只加新的 `V{n}__*.sql`**,绝不改已发布的迁移脚本(Flyway checksum)。

## 1. 插件目录结构

以 `plugins/jnimble-plugin-demo-crm` 为最小模板:

```
jnimble-plugin-<feature>/
├── pom.xml
└── src/main/
    ├── java/com/jnimble/plugin/<feature>/
    │   ├── <Feature>PluginBoot.java          # 必需:实现 PluginBoot
    │   ├── <Feature>PluginConfiguration.java # 推荐:插件 Spring 子上下文入口
    │   ├── controller/                       # 可选:Spring MVC 控制器
    │   ├── service/                          # 可选:业务服务
    │   ├── mapper/                           # 可选:MyBatis-Plus Mapper
    │   └── model/entity/                     # 可选:实体
    └── resources/
        ├── META-INF/jnimble-plugin.json      # 必需:插件描述符
        ├── templates/plugin/<pluginId>/      # 模板(路径必须以 plugin/<pluginId>/ 开头)
        │   ├── fragment/                     # 侧边栏/顶栏等 hook 片段
        │   └── admin/ 或 page/               # 页面模板
        ├── i18n/                             # 国际化(messages.properties / _zh_CN / _en_US)
        ├── db/migration/plugin/<pluginId>/   # 插件级 Flyway 迁移(V1__*.sql ...)
        └── static/plugin/<pluginId>/         # 静态资源(css/js)
        └── src/test/java/...                 # 至少一个 boot() 单元测试
```

`<pluginId>` 是描述符里的 `id`(如 `order-core`),与模块名 `jnimble-plugin-order-core` 对应。

## 2. 描述符 `META-INF/jnimble-plugin.json`

```json
{
  "schemaVersion": "1.0",
  "id": "order-core",
  "name": "Order Core Plugin",
  "nameKey": "plugin.order-core.name",
  "description": "Core order management plugin",
  "descriptionKey": "plugin.order-core.description",
  "version": "1.0.0",
  "platformVersion": "0.1.x",
  "author": "JNimble Team",
  "bootClass": "com.jnimble.plugin.order.OrderPluginBoot",
  "spring": {
    "configurationClass": "com.jnimble.plugin.order.OrderCorePluginConfiguration"
  },
  "i18n": { "basename": "i18n/messages" },
  "admin": {
    "entry": "/orders",
    "labelKey": "order.admin.title",
    "permission": "order-core.admin.view"
  },
  "dependencies": [
    { "pluginId": "menu-manager", "version": "1.x", "required": true }
  ],
  "permissions": [
    { "code": "order-core.admin.view", "name": "View order admin", "nameKey": "permission.order-core.admin.view" }
  ],
  "migration": {
    "enabled": true,
    "location": "classpath:db/migration/plugin/order-core",
    "table": "flyway_schema_history_order_core",
    "baselineOnMigrate": true,
    "failOnError": true
  }
}
```

| 字段 | 必需 | 说明 |
|------|:---:|------|
| `schemaVersion` | ✅ | 固定 `"1.0"` |
| `id` | ✅ | 全局唯一插件 id,决定权限前缀、包命名、迁移表名 |
| `name` / `nameKey` | ✅ | 显示名(回落值 / i18n key) |
| `description` / `descriptionKey` | | 描述 |
| `version` | ✅ | 插件语义版本 |
| `platformVersion` | ✅ | 兼容的平台版本表达式,如 `0.1.x` |
| `author` / `website` | | 元信息 |
| `bootClass` | ✅ | 实现 `PluginBoot` 的全限定类名 |
| `spring.configurationClass` | 推荐 | 插件 Spring 子上下文的 `@Configuration` 类(有 controller/service 时必须) |
| `i18n.basename` | 推荐 | i18n 资源 basename,如 `i18n/messages` |
| `admin.entry` | | 插件后台落地路径(相对插件命名空间) |
| `admin.labelKey` / `admin.permission` | | 后台入口标题 key / 访问所需权限 |
| `dependencies[]` | | 依赖的其它插件 `{pluginId, version, required}`(见 §7) |
| `permissions[]` | | 声明权限 `{code, name, nameKey, description?, descriptionKey?}`,`code` 必须以 `{id}.` 开头 |
| `migration` | | 插件级 Flyway 配置,见 §5 |
| `configuration` | | 可选的"声明式配置表单"描述(见 §4.5) |

> 未知字段会被忽略,但请勿随意添加未支持的字段。

## 3. Boot 类与扩展点

`PluginBoot.boot(PluginContext)` 在插件启用时调用,**所有能力通过 `context` 注册**,不直接触碰框架内部。

```java
package com.jnimble.plugin.order;

import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.resource.AssetDefinition;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteMethod;
import java.util.Map;

public class OrderPluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        String pluginId = context.descriptor().id();

        // 1) 路由:路径相对插件命名空间 => 实际 URL /admin/plugins/{pluginId}{path}
        context.routes().register(new RouteDefinition(
                "/orders", RouteMethod.GET, "plugin/order-core/admin/orders", "order-core.admin.view"));

        // 2) 侧边栏 hook:注册一个片段模板
        context.hooks().register("admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/order-core/fragment/sidebar-orders",   // 模板路径
                        Map.of("pluginId", pluginId),                   // 片段模型
                        15,                                             // 排序(order 越小越靠前)
                        "order-core.admin.view",                        // 可见所需权限
                        null));                                         // activeWhen(可选)

        // 3) 静态资源:classpath 目录映射到插件资源命名空间
        context.assets().register(new AssetDefinition(
                "/", "classpath:static/plugin/order-core/", true));
    }

    @Override
    public void stop(PluginContext context) {
        // 释放插件自己持有的资源(线程、连接等);通过 context 注册的会由框架回收
    }
}
```

### 3.1 路由 `RouteDefinition(path, method, view, permission)`
- `path` 相对插件命名空间,最终 URL = `/admin/plugins/{pluginId}{path}`。
- `view` 是 Thymeleaf 模板名(如 `plugin/order-core/admin/orders`),或为 `null` 表示由控制器处理。
- `permission` 非空时,访问需具备该权限(服务端校验,不依赖前端隐藏)。
- 仅 GET 页面通常同时用 `context.routes().register(...)` 声明 + 一个 Spring 控制器渲染模板(见 §4)。

### 3.2 侧边栏 / 顶栏 hook 点
片段模板路径必须以 `plugin/{pluginId}/` 开头。可用 hook 点:

| hook 点 | 位置 |
|---|---|
| `admin.layout.sidebar.start` | 侧边栏顶部 |
| `admin.layout.sidebar` | 侧边栏通用插槽(最常用) |
| `admin.layout.sidebar.sys.start` / `.end` | "系统"分组首/尾 |
| `admin.layout.sidebar.{plugins,roles,users}.{before,after}` | 各个内置菜单项前/后 |
| `admin.layout.sidebar.end` | 侧边栏底部 |
| `admin.layout.topbar` | 顶栏 |

### 3.3 静态资源 `AssetDefinition(requestPath, resourceLocation, cacheable)`
把插件自己的 css/js 暴露给页面,例如 `new AssetDefinition("/", "classpath:static/plugin/crm/", true)`。

## 4. Spring 子上下文与控制器

声明了 `spring.configurationClass` 后,框架会为**每个插件创建独立的 Spring 子上下文**,在该子上下文里加载你的 `@Configuration`。插件 bean 之间互相隔离。

### 4.1 配置类

```java
package com.jnimble.plugin.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackages = "com.jnimble.plugin.order",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.jnimble\\.plugin\\.order\\.table\\..*"   // 排除兄弟插件的子包
        )
)
@MapperScan(basePackages = "com.jnimble.plugin.order.mapper")
public class OrderCorePluginConfiguration {
}
```

### 4.2 控制器

插件控制器就是普通的 Spring MVC 控制器,会被自动注册到主 MVC:

```java
@Controller
@RequestMapping("/admin/plugins/order-core")
public class OrderAdminController {

    private final OrderService orderService;

    public OrderAdminController(OrderService orderService) { this.orderService = orderService; }

    @GetMapping("/orders")                       // 页面
    public String ordersPage() { return "plugin/order-core/admin/orders"; }

    @GetMapping("/orders/list")                  // 供页面 AJAX 调用
    @ResponseBody
    public List<OrderEntity> listOrders(@RequestParam(required = false) String status) {
        return orderService.listOrders(status, null, null, null);
    }
}
```

需要细粒度权限校验时,注入平台的 `com.jnimble.platform.auth.ControllerAuthorization` 并调用 `requirePermission(...)`。

### 4.3 ⚠️ 组件扫描的坑(务必遵守)

**问题**:`@ComponentScan(basePackages = "com.jnimble.plugin.order")` 会把子包 `com.jnimble.plugin.order.table`(即 order-table 插件)一并扫进来,导致兄弟插件的 bean/控制器被加载两次(MVC 会报 `Ambiguous mapping`)。

**规则**:每个插件的包路径必须**互不为父子**。推荐直接按插件 id 定独立段:

- ✅ `com.jnimble.plugin.order.core` 与 `com.jnimble.plugin.order.table`
- ✅ `com.jnimble.plugin.printer.core` 与 `com.jnimble.plugin.printer.feie`
- ❌ `com.jnimble.plugin.order`(order-core)与 `com.jnimble.plugin.order.table`(order-table)——后者是前者的子包

本仓库现有的 `order-core` / `printer-core` / `printer-feie` 之所以能工作,是靠手工 `excludeFilters` 兜底(而 `printer-core` 曾漏掉排除 `feie`,导致 `printer-feie` 插件启用失败,后已修复)。**新插件请直接用独立包段,不要依赖 excludeFilters。**

### 4.4 数据库访问
- 实体用 MyBatis-Plus 注解(`@TableName` / `@TableId`)。
- Mapper 用 `@Mapper` 接口,放在 `...mapper` 包并纳入 `@MapperScan`。
- 平台单表数据访问优先用 `MapperUtils`,不要直接裸调 `BaseMapper` CRUD。

### 4.5 声明式配置表单(可选)
若插件需要后台可配置项,用描述符 `configuration` 声明字段(类型/默认值/选项),框架会渲染配置表单并存储。参考 `jnimble-plugin-payment` 的 `payment.configuration.*`。

## 5. 数据库迁移(插件级 Flyway)

- 目录:`src/main/resources/db/migration/plugin/{pluginId}/`,文件名 `V{n}__{desc}.sql`。
- 由描述符 `migration` 指定:`location` / `table`(`flyway_schema_history_{pluginId 中 - 换成 _}`)/`baselineOnMigrate`/`failOnError`。

铁律:
1. **只加新脚本,永不改旧脚本**(checksum 变化会导致启动失败)。
2. 脚本尽量幂等(`CREATE TABLE IF NOT EXISTS` 等),保证全新库与既有库一致。
3. **不在运行期手写 DDL**(`CREATE/ALTER`)绕过 Flyway。
4. 卸载插件不删业务表——迁移脚本里**不要写 `drop table`**。

## 6. 国际化(i18n)

- 文件:`src/main/resources/i18n/messages.properties`(默认)、`messages_zh_CN.properties`、`messages_en_US.properties`。
- 描述符声明 `"i18n": { "basename": "i18n/messages" }`。
- 新增条目要**三个文件同步**,否则某些语言会渲染成 `??key??`。
- 中文直接用 UTF-8 原文(不用 `\uXXXX`)。

## 7. 依赖

### 7.1 依赖平台(可用)
插件 pom 依赖框架模块(compile),源码可 import:
- `com.jnimble.sdk.*` —— 插件契约(hook/route/asset/plugin)。
- `com.jnimble.platform.*` —— 权限(`ControllerAuthorization`)、持久化(`MapperUtils`、实体基类)等。

### 7.2 依赖其它插件(用描述符声明)
插件间依赖(如 order-table 依赖 order-core)在描述符 `dependencies` 声明,框架按依赖顺序装配类加载器:

```json
"dependencies": [
  { "pluginId": "order-core", "version": "1.x", "required": true },
  { "pluginId": "menu-manager", "version": "1.x", "required": true }
]
```

- 插件 JAR **不内联**依赖的插件,也不内联框架模块;运行时由宿主/依赖插件的类加载器提供。
- 因此**必须显式声明依赖**,否则会 `NoClassDefFoundError`。
- 依赖的插件需先被安装/启用(目录模式下按 `dependencies` 顺序自动排序)。

## 8. 构建、运行与热部署

见 [`doc/dev-workflow.md`](dev-workflow.md)。速览:

```bash
# 1) 首次:安装框架到本地仓库
cd jnimble-framework && mvn clean install -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true && cd ..

# 2) 构建插件:JAR 自动输出到 jnimble-framework/jnimble-starter/data/plugins/
bash scripts/build-plugins.sh

# 3) 启动(从 data/plugins/ 加载,运行期热部署)
cd jnimble-framework && mvn -pl jnimble-starter spring-boot:run
```

开发循环:改插件 → `mvn -f plugins/pom.xml package`(或 `scripts/build-plugins.sh`)→ 新 JAR 落盘 → 运行中的应用**热替换**该插件,无需重启。

## 9. 测试

- 每个插件至少有一个 `boot()` 单元测试,验证 hook/route 注册:

```java
class CrmPluginBootTest {
    @Test
    void registersSidebarHookAndRoute() {
        // 用 mock/fake 的 PluginContext 断言 hooks().register / routes().register 被调用
    }
}
```

- 平台/框架级改动必须有测试(H2 内存库 + `@SpringBootTest`)。
- 运行:`mvn test`(surefire 已配置 mockito javaagent,勿删)。

## 10. 新增一个插件的完整步骤

以新增 `my-notes` 插件为例:

1. **建模块**:复制 `plugins/jnimble-plugin-demo-crm` 为 `plugins/jnimble-plugin-my-notes`,改 `artifactId`。
2. **注册模块**:在 `plugins/pom.xml` 的 `<modules>` 加 `jnimble-plugin-my-notes`,在 `<dependencyManagement>` 加其坐标(版本 `0.1.0-SNAPSHOT`)。
3. **改包名/类名**:`com.jnimble.plugin.mynotes`(独立包段),`MyNotesPluginBoot` / `MyNotesPluginConfiguration`。
4. **改描述符**:`id` = `my-notes`,`bootClass`/`spring.configurationClass` 指向新类,`permissions[].code` 以 `my-notes.` 开头,`migration.location` = `classpath:db/migration/plugin/my-notes`、`table` = `flyway_schema_history_my_notes`。
5. **加业务**:controller/service/mapper/entity、templates(`plugin/my-notes/...`)、i18n(三份)、migration(`V1__init.sql`)。
6. **写测试**:`MyNotesPluginBootTest`。
7. **构建并验证**:
   ```bash
   bash scripts/build-plugins.sh
   ```
   启动后侧边栏出现插件入口,页面可访问,`jnimble_plugin_state` 中 `source=JAR,status=ENABLED`。

## 11. 常见坑清单

| 现象 | 原因 | 解决 |
|------|------|------|
| 启动报 `Ambiguous mapping` | 插件 `@ComponentScan` 扫到了兄弟插件的子包 | 用独立包段;或加 `excludeFilters`(不推荐) |
| 插件 `NoClassDefFoundError` | 依赖了其它插件/框架模块但未声明/未置于 classpath | 描述符 `dependencies` 显式声明;框架模块由宿主提供 |
| 权限不生效 | 权限码未以 `{pluginId}.` 开头 | 改权限码前缀 |
| 迁移启动失败:checksum | 改了已发布的 `V*.sql` | 只加新版本脚本 |
| `??key??` | i18n 三份文件不同步 | 三份补齐 |
| 插件启用失败、日志有异常 | 子上下文 bean 创建失败(缺依赖/类) | 看 `jnimble_plugin_state.last_error` 与应用日志 |

## 12. 参考插件

| 插件 | 可参考的点 |
|------|-----------|
| `jnimble-plugin-demo-crm` | 最小骨架(descriptor + boot + hook + route + asset + i18n + 测试) |
| `jnimble-plugin-order-core` | 完整业务:多 controller/service/mapper、多迁移、hook 注册 |
| `jnimble-plugin-order-table` | 依赖 order-core / menu-manager、复杂路由 |
| `jnimble-plugin-printer-core` | SPI 扩展点(驱动注册表)、调度 |
| `jnimble-plugin-printer-feie` | 依赖 printer-core、实现 SPI 驱动 |
| `jnimble-plugin-payment` | 声明式配置表单 |
| `jnimble-plugin-license-issuer` | 依赖框架模块 `jnimble-license-sdk` 的示例 |
