# 仓库与分发方案(框架一库 + 插件仓库)

> ## 最新决定(以此为准)
> - **框架**:独立仓库 `what123/JNimble`(开源)。
> - **插件**:**当前仓库 `jnimble-order-plugin` 即插件仓库**。现阶段所有插件就先放在这一个仓库里;
>   下方"按域分成多个组仓库"的方案**保留为将来可选**,不急于落地。
> - **交付方式**:插件仓库打 tag → CI 构建 → **GitHub Release 挂 JAR**(或整套 zip);
>   框架仓库放《点餐系统组装指南》,列出插件与下载地址;用户下载 JAR 放进 `data/plugins/` 即可。
> - **因此**:不需要独立的"集成/发行库";"组装"= 指南 + 下载,而非脚本或框架运行时拉取构建。
> - 下列 §1~§13 描述的是**将来可选的**多仓按域拆分方案,供需要独立发布时参考。

> 状态:**方案草稿(未落地)**。

## 1. 形态总览

```
JNimble                          # 框架(一个库,多模块)          -> 发布 Maven 制品
jnimble-plugins-ordering         # 点餐组:  menu-manager / order-core / order-table / payment / scan-consumer
jnimble-plugins-printing         # 打印组:  printer-core / printer-feie
jnimble-plugins-license          # 授权组:  license-issuer(闭源)
jnimble-plugins-samples          # 示例组:  demo-crm
jnimble-order-plugin             # 集成/发行库: BOM + 组装可运行包(由当前仓库演进)
```

规则:
- 组 = 一个 Maven reactor(多模块)+ 一个版本单元;组内插件仍是各自独立的 Maven 制品。
- 组内联调走 reactor;组间依赖走**已发布的制品**。
- 框架必须先制品化(发布 `jnimble-bom` 与各模块),否则分组无法独立构建。

## 2. 分组清单

| 组 | 仓库 | 包含插件 | 组内依赖 | 跨组依赖 |
|---|---|---|---|---|
| 框架 | `what123/JNimble` | `jnimble-parent`, `jnimble-bom`, `jnimble-plugin-sdk`, `jnimble-license-sdk`, `jnimble-license-core`, `jnimble-kernel`, `jnimble-platform`, `jnimble-admin-shell`, `jnimble-starter`, `jnimble-demo-plugin` | — | — |
| 点餐组 | `jnimble-plugins-ordering` | `menu-manager`, `order-core`, `order-table`, `payment`, `scan-consumer` | `order-table→order-core,menu-manager`;`payment→order-core`;`scan-consumer→order-core,order-table,menu-manager` | 框架 |
| 打印组 | `jnimble-plugins-printing` | `printer-core`, `printer-feie` | `printer-feie→printer-core` | 点餐组:`order-core` |
| 授权组 | `jnimble-plugins-license` | `license-issuer`(闭源) | — | 框架:`jnimble-license-sdk` |
| 示例组 | `jnimble-plugins-samples` | `demo-crm` | — | 框架 |
| 集成/发行 | `jnimble-order-plugin` | `jnimble-plugins-bom` + 组装工程 | — | 框架 + 全部组 |

分组依据(基于描述符实际依赖):把强耦合、一起演进的插件放同组。
`menu-manager`(菜单/菜品/规格)目前**只被点餐域依赖**(order-table、scan-consumer),
故并入点餐组,使点餐组内部依赖闭合、跨组依赖最小化;打印组单向依赖点餐组的 `order-core`。
若将来出现非点餐域也要复用菜单,再把 `menu-manager` 拆出为独立基础组(制品边界已存在,零重构)。

## 3. 依赖 DAG(组间,单向无环)

```
JNimble(框架)
  ├─ jnimble-plugins-ordering
  │        └─ jnimble-plugins-printing
  ├─ jnimble-plugins-license
  └─ jnimble-plugins-samples
        (以上全部) ──> jnimble-order-plugin(发行)
```
发布顺序:框架 → 点餐组 → 打印组;(授权组、示例组任意)→ 发行库。

## 4. 各仓库目录结构

### 4.1 组仓库(以点餐组为例)
```
jnimble-plugins-ordering/
├── pom.xml                       # 组 parent(packaging=pom):模块聚合 + 版本管理
├── jnimble-plugin-order-core/
│   ├── pom.xml
│   └── src/main/... (java/resources,含 META-INF/jnimble-plugin.json、db/migration/plugin/order-core、templates/plugin/order-core)
├── jnimble-plugin-order-table/
├── jnimble-plugin-payment/
└── jnimble-plugin-scan-consumer/
```

### 4.2 发行库(当前仓库演进)
```
jnimble-order-plugin/
├── pom.xml                       # 集成 parent
├── bom/
│   └── pom.xml                   # jnimble-plugins-bom:锁定各组件版本
├── assembly/
│   └── pom.xml                   # 组装:拉取框架 starter + 各组插件 JAR -> 发行包
├── scripts/                      # 运行/构建脚本(现有 build-plugins.sh 演进)
└── doc/
```

## 5. 坐标与版本约定

| 对象 | groupId | 版本策略 |
|---|---|---|
| 框架 | `com.jnimble` | 独立语义版本,如 `0.1.0`(打 tag) |
| 插件(全部组) | `com.jnimble.plugins` | 按组独立版本,组内插件同版本,如点餐组 `1.2.0` |

- 插件**不再**与框架共享 `0.1.0-SNAPSHOT`;插件版本与框架版本解耦,靠 `platformVersion`(描述符)与 `jnimble-plugin-sdk` 版本表达兼容性。
- 跨组引用统一由 `jnimble-plugins-bom` 给版本;框架依赖由 `jnimble-bom` 给版本。

## 6. 组 parent pom 骨架(以点餐组为例)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.jnimble.plugins</groupId>
  <artifactId>jnimble-plugins-ordering</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>

  <properties>
    <jnimble.version>0.1.0</jnimble.version>                 <!-- 框架版本 -->
    <jnimble.plugins.version>1.0.0</jnimble.plugins.version> <!-- 跨组 BOM 版本 -->
    <maven.compiler.release>21</maven.compiler.release>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.jnimble</groupId>
        <artifactId>jnimble-bom</artifactId>
        <version>${jnimble.version}</version>
        <type>pom</type><scope>import</scope>
      </dependency>
      <dependency>
        <groupId>com.jnimble.plugins</groupId>
        <artifactId>jnimble-plugins-bom</artifactId>
        <version>${jnimble.plugins.version}</version>
        <type>pom</type><scope>import</scope>
      </dependency>
      <!-- 组内插件(跟随组版本) -->
      <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-menu-manager</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-order-core</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-order-table</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-payment</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-scan-consumer</artifactId><version>${project.version}</version></dependency>
    </dependencies>
  </dependencyManagement>

  <modules>
    <module>jnimble-plugin-menu-manager</module>
    <module>jnimble-plugin-order-core</module>
    <module>jnimble-plugin-order-table</module>
    <module>jnimble-plugin-payment</module>
    <module>jnimble-plugin-scan-consumer</module>
  </modules>

  <build>
    <pluginManagement>
      <!-- 复用:编译/校验设置;插件 JAR 输出到 data/plugins 的 antrun 也在此统一声明 -->
    </pluginManagement>
  </build>
</project>
```

## 7. 插件 pom 骨架(以 order-table 为例)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.jnimble.plugins</groupId>
    <artifactId>jnimble-plugins-ordering</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>jnimble-plugin-order-table</artifactId>

  <dependencies>
    <!-- 框架(SDK 是稳定契约;platform 为平台能力,版本来自 jnimble-bom) -->
    <dependency><groupId>com.jnimble</groupId><artifactId>jnimble-plugin-sdk</artifactId></dependency>
    <dependency><groupId>com.jnimble</groupId><artifactId>jnimble-platform</artifactId></dependency>

    <!-- 组内依赖(跟随组版本) -->
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-order-core</artifactId></dependency>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-menu-manager</artifactId></dependency>

    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

> 跨组依赖的写法同理,只是坐标来自另一组,版本由 `jnimble-plugins-bom` 统一给出。
> 例:打印组的 `printer-core` 依赖点餐组的 `order-core` ——
> `<dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-order-core</artifactId></dependency>`(不写版本,由 BOM 提供)。

## 8. 描述符依赖与 Maven 依赖的对应(必须一致)

- **Maven 依赖**(compile):保证编译期能引用到依赖插件的类。
- **描述符 `dependencies`**(运行时):保证运行期按顺序装配类加载器。

`order-table` 两处都要声明 `order-core` 与 `menu-manager`:

```json
"dependencies": [
  { "pluginId": "order-core",   "version": "1.x", "required": true },
  { "pluginId": "menu-manager", "version": "1.x", "required": true }
]
```

> 跨组的 `menu-manager` 由发行库把其 JAR 一并打包/落盘,运行时才能被解析到。

## 9. 发行库(BOM + 组装)

### 9.1 `jnimble-plugins-bom`(跨组版本锁定)
```xml
<project ...>
  <groupId>com.jnimble.plugins</groupId>
  <artifactId>jnimble-plugins-bom</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <dependencyManagement><dependencies>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-menu-manager</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-order-core</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-order-table</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-payment</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-scan-consumer</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-printer-core</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-printer-feie</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-license-issuer</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.jnimble.plugins</groupId><artifactId>jnimble-plugin-demo-crm</artifactId><version>1.0.0</version></dependency>
  </dependencies></dependencyManagement>
</project>
```

> 可见性区分:公开的 `jnimble-plugins-bom` **只应包含开源插件坐标**(发 Central 供公众使用);含私有插件坐标的 BOM 留在私有仓库(见 §13.2)。

### 9.2 组装工程
- 依赖框架 `jnimble-starter`(可运行入口)+ 需要纳入的插件制品。
- 用 `maven-dependency-plugin` 把各插件 JAR 复制到发行包的 `data/plugins/`;用 `maven-assembly-plugin` 打成 zip/tar。
- 产物即"一套可运行发行版":框架 + 选定插件集合。

## 10. 本地多仓联调

- 同时 checkout `JNimble` 与本组仓库(平级目录)。
- 首次/框架变更后:`cd JNimble && mvn install`(装框架到本地库)。
- 组内开发:`cd jnimble-plugins-ordering && mvn -pl <plugin> package` 走 reactor。
- 跨组开发:依赖组先用 `mvn install` 装成 `-SNAPSHOT`,或在组 parent 里用 `-Djnimble.plugins.version=1.0.0-SNAPSHOT` 覆盖。
- 联调热部署:发行包/本地 start 应用(顺序方案 A:JAR 放 `data/plugins/`),运行期热替换。

## 11. 迁移步骤(从当前单仓到多仓)

1. **框架**:给 `JNimble` 加发布配置(`distributionManagement`/`deploy`),把几处本地改动(license-sdk 依赖、logo 放行、V5 迁移、CORS/CSRF)提交回上游,打 tag `0.1.0`,发布。
2. **BOM**:在发行库建 `jnimble-plugins-bom`。
3. **拆组**:为每组建仓库;用 `git subtree split -P plugins/<plugin>`(或 `git filter-repo`)把各插件历史迁到对应组仓库,保留提交历史。
4. **改坐标**:各组 parent 改为导入 `jnimble-bom` + `jnimble-plugins-bom`;插件 groupId 改 `com.jnimble.plugins`;组内/跨组依赖按第 6/7 节声明。
5. **当前仓库**:转为发行库(保留 `doc/`、`scripts/`、新增 `bom/`、`assembly/`);移除 `plugins/` 源码与 `jnimble-framework` submodule(改为依赖已发布框架)。
6. **CI**:每组一份流水线(构建→测试→发布);发行库一份(拉取各组→出包)。

## 12. 已确认决策

| # | 决策 |
|---|------|
| 1 | 插件统一 groupId 用 `com.jnimble.plugins`;框架保持 `com.jnimble` |
| 2 | `demo-crm` → 独立示例组仓库 `jnimble-plugins-samples` |
| 3 | `license-issuer` → 单独仓库 `jnimble-plugins-license`,**闭源**(依赖框架 `jnimble-license-sdk`) |
| 4 | 保留各插件 git 提交历史(迁移用 `git subtree split` / `git filter-repo`) |
| 5 | `menu-manager` 并入点餐组 `jnimble-plugins-ordering` |
| 6 | **框架开源对外发布;插件部分开源、部分私有**(见 §13) |
| 7 | **私有插件可依赖开源插件;开源插件不得依赖私有插件**(可见性单调,见 §13.2) |

仍待办:
- 框架侧的本地未提交改动(license-sdk 依赖、logo 放行、V5 迁移、CORS/CSRF)先回上游,再打 tag 发布。

## 13. 发布与分发策略(框架开源;插件部分开源、部分私有)

商业模式:**框架开源免费;插件分为开源(免费)与私有(售卖)两类,私有插件不向公众分发**。

### 13.1 发布目标

| 内容 | 仓库 | 可见性 |
|------|------|--------|
| 框架 `com.jnimble:*`(`jnimble-bom` / `plugin-sdk` / `platform` / `kernel` / `admin-shell` / `starter`) | Maven Central(或 GitHub Packages 公开) | 公开,匿名可依赖 |
| **开源**插件 `com.jnimble.plugins:*` | Maven Central | 公开,匿名可依赖 |
| **私有**插件 `com.jnimble.plugins:*` | **私有 Maven 仓库**(Nexus / GitHub Packages 私有) | 私有,仅 CI/授权方可见 |

- **私有插件不发 Central**;开源插件发 Central。
- 组间依赖、发行库组装都需能解析两条来源(公开仓库 + 私有仓库);私有仓库凭据配在 `settings.xml` / CI secrets。
- 插件只依赖**公开的**框架 SDK/平台,方向仍是"插件 → 平台"。

### 13.2 可见性硬规则(必须遵守)

- **私有插件可以依赖开源插件;开源插件不得依赖私有插件。**
- 原因:开源插件要独立构建并发布到 Central,若它依赖私有制品,别人拉不到该依赖,构建/运行必然失败。
- 等价说法:**可见性沿依赖方向单调**——`私有 → 开源` 允许,`开源 → 私有` 禁止。
- 推论:想把某个上层插件开源,它**依赖链上的所有插件都必须开源**;反之私有插件可自由依赖开源插件。
- BOM 也要区分:开源 BOM(`jnimble-plugins-bom`,只含开源坐标)发 Central;含私有坐标的私有 BOM 留在私有仓库。
- 落地建议:让可见性沿"**基础/通用 → 行业增值**"分层——基础插件开源(免费引流、生态),核心/增值插件私有(售卖)。

### 13.3 客户端如何拿到插件(对公售卖)

两种交付方式,建议**以发行包为主**:

1. **发行包(推荐)**:发行库组装出"框架 + 选定插件 + 配置 + license"的压缩包/镜像,客户离线部署。
   - 客户**不需要**访问你的私有 Maven,降低泄露与运维成本。
   - 与"方案 A"契合:插件 JAR 落在 `data/plugins/`。
2. **私有只读 Maven**:给客户(按授权)发放只读凭据,让其自建应用自行组合插件。
   - 需要更细的权限/审计/配额;GitHub Packages 的权限模型对"多客户"并不友好,Nexus 更合适。

### 13.4 授权与防扩散(重要)

私有仓库只控制**下载访问**,JAR 仍可被反编译。商业保护需叠加:

- **运行时 license 校验**(框架已具备):`jnimble-license-sdk` + `jnimble_plugin_license` 表,按 machine code + 签名 token 绑定;`license-issuer` 负责签发。这是主要手段。
- 插件在描述符声明 license 策略(如 `license.required / productCode / policy`),由框架在启用时强制校验。
- 必要时对插件 JAR 做**混淆**(ProGuard/R8)或关键逻辑加密,提高反编译成本。

### 13.5 仓库选型建议

- 内部 CI 发布插件优先 **Nexus / Artifactory**:支持对多客户发只读账号、代理 Central、配额与审计。
- 前期图快可先用 **GitHub Packages 私有包**起步;规模变大或需对多客户分发时再迁 Nexus。

### 13.6 客户端消费方式对比

| 方式 | 客户是否需访问你的私有仓库 | 控制力 | 运维成本 |
|------|:---:|:---:|:---:|
| 发行包交付(推荐) | 否 | 高(内容由你组装) | 低 |
| 私有只读 Maven | 是 | 中(靠凭据/配额) | 中高 |
