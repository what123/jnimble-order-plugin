# 平板客户端（UniAPP）需求与设计

> 状态：方案已确认（2026-10-08）
> 关联仓库：本仓库提供后端 API；UniAPP 客户端为独立仓库（同级目录 `../jnimble-order-app`）

## 1. 背景与目标

在现有 JNimble 点餐系统（Web 后台 + 扫码点餐 API）基础上，新增一个 **UniAPP 平板客户端**，可安装到 Android 平板/手机，支持两种使用模式：

- **顾客模式**：顾客自助点餐（支持规格选配），体验与现有线上扫码点餐一致。
- **员工模式**：员工登录后完成 POS 点餐（桌台、开台、点菜、下单、结账）、后厨制作、订单查看，并可在 App 内管理**菜品**与**打印机**。

后端遵循现有插件体系：
- **顾客模式**：直接复用并增强 `scan-consumer` 插件（`/api/consumer/**`），增强向后兼容。
- **员工模式**：新增插件 `jnimble-plugin-app-client`（`/api/app/**`），业务逻辑全部复用现有插件服务。
- 不修改框架源码；线上点餐与 Web 后台行为不受影响（增强均为追加式）。

## 2. 范围

### 2.1 V1 包含

| 模块 | 内容 |
|------|------|
| App 端 | 顾客模式（绑定/进店、菜单选规格、购物车、下单、订单查询）；员工模式（登录、POS 点餐、后厨制作、订单查看、菜品管理、打印机管理、设置） |
| 后端 API | 顾客：规格选配 + 图片公开 + 门店/桌台列表（scan-consumer、menu-manager 增强）；员工：鉴权、菜品/分类管理、图片上传、打印机管理、POS、后厨、订单（app-client 插件） |
| 交付 | 本仓库插件 JAR；App 独立仓库（UniAPP 源码 + HBuilderX 打包说明） |

### 2.2 V1 不包含（预留后续）

- 在线支付（沿用现有结账支付方式：现金/线下；支付插件已提供 SPI）
- 打印模板/BPMN 节点设计（Web 后台已有）
- 顾客账号体系（沿用现有匿名 consumer token 机制）

## 3. 总体架构

```
┌────────────────────────────┐          ┌──────────────────────────────────────────┐
│ UniAPP 平板客户端（独立仓库）  │          │ JNimble 服务端（本仓库插件体系）              │
│                            │          │                                          │
│  顾客模式 ──┐               │  HTTPS   │  /api/consumer/**  scan-consumer（增强）    │
│             ├───────────────┼─────────▶│     规格/图片/门店桌台                       │
│  员工模式 ──┘               │          │  /api/app/**       app-client（新增）       │
│  菜品/打印机/后厨/订单         │          │     鉴权 + 员工侧 API 编排                   │
└────────────────────────────┘          │  /api/menu/images/** menu-manager（公开图片）  │
                                        └──────────────────────────────────────────┘
```

- 顾客模式调用 `/api/consumer/**`（与线上扫码点餐同一套接口，本次增强规格与图片）。
- 员工模式调用 `/api/app/**`，统一响应结构 `{code, msg, data}`。
- 员工账号即 JNimble 后台账号，权限沿用现有权限码（RBAC 与 Web 后台一致）。

## 4. 后端设计

### 4.1 插件改动总览

| 插件 | 改动 | 说明 |
|------|------|------|
| `menu-manager` | 追加公开图片读取接口 | `GET /api/menu/images/{fileName}`（无需登录），复用 `MenuItemImageStorageService` |
| `scan-consumer` | 规格选配、图片 URL、门店/桌台列表 | 全部为追加式增强，向后兼容 |
| `app-client`（新增） | 员工侧全部 API | `jnimble-plugin-app-client`，`/api/app/**` |

### 4.2 新增插件定义（app-client）

| 项 | 值 |
|----|----|
| pluginId | `app-client` |
| artifactId | `jnimble-plugin-app-client` |
| 包名 | `com.jnimble.plugin.app` |
| 描述符依赖 | `menu-manager`、`order-core`、`order-table`、`printer-core`（均 required；POS 依赖 order-table，后厨/订单依赖 order-core，菜品依赖 menu-manager，打印机依赖 printer-core） |
| Admin 页面 | 无（App 自身即界面）；不注册侧边栏 |
| 迁移 | `db/migration/plugin/app-client`，历史表 `flyway_schema_history_app_client` |

### 4.3 鉴权设计（员工）

框架无 JWT/Token 体系，插件自建轻量 token：

1. `POST /api/app/auth/login`：`UserAccountService.findByUsername` + `PasswordEncoder.matches` 校验（账号状态须 ACTIVE），签发随机 token（SecureRandom，64 位十六进制），写入 `app_auth_token` 表，有效期 30 天。
2. 请求携带 `Authorization: <token>`（与 scan-consumer 传法一致）。
3. 员工接口在控制器内显式校验：`AppAuthService.requirePermission(request, code)`（沿用现有插件显式 `requirePermission` 风格；框架插件子上下文不支持注册拦截器）。
4. token 失效/过期返回 HTTP 401 + `{code:401}`；无权限返回 403。
5. 登录/`me` 接口返回该用户拥有的权限码列表（App 用于控制入口显隐）。

权限码沿用现有（不新增）：

| 权限码 | 覆盖功能 |
|--------|----------|
| `menu-manager.item.manage` | 菜品增删改、上下架、图片上传 |
| `menu-manager.category.manage` | 分类增删改 |
| `printer-core.config` | 打印机增删改、清队列 |
| `order-table.pos.view` | 桌台看板、点菜目录、会话查询、加菜 |
| `order-table.pos.open` | 开台、拼台、并台、转台、改人数、清台 |
| `order-table.pos.confirm` | 确认/退回顾客提交单 |
| `order-table.pos.settle` | 结账 |
| `order-core.kitchen.view` | 后厨队列查看 |
| `order-core.kitchen.operate` | 后厨制作/完成 |
| `order-core.kitchen.print` | 后厨手动打印 |

### 4.4 员工侧 API 清单（app-client，`/api/app/**`）

统一响应：`{code:0, msg:"success", data:{...}}`；错误：`code!=0`（401/403/400/409 对应 HTTP 状态码）。

#### 4.4.1 认证

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/app/auth/login` | 登录，body `{username,password}`；返回 `{token,expiresAt,user:{id,username,displayName,permissions[]}}` | 公开 |
| POST | `/api/app/auth/logout` | 注销当前 token | 登录 |
| GET | `/api/app/auth/me` | 当前用户信息 + 权限列表 | 登录 |

#### 4.4.2 菜品管理（复用 MenuItemService / CategoryService / MenuItemImageStorageService）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/app/menu/categories` | 分类列表 | item/category manage |
| POST | `/api/app/menu/categories` | 新建分类 | category.manage |
| PUT | `/api/app/menu/categories/{id}` | 修改分类 | category.manage |
| DELETE | `/api/app/menu/categories/{id}` | 删除分类 | category.manage |
| GET | `/api/app/menu/items?categoryId=&keyword=&status=` | 菜品列表（含规格组/图片） | item.manage |
| POST | `/api/app/menu/items` | 新建菜品（含规格组、图片） | item.manage |
| PUT | `/api/app/menu/items/{id}` | 修改菜品 | item.manage |
| POST | `/api/app/menu/items/batch-status` | 批量上下架 | item.manage |
| POST | `/api/app/menu/images` | 图片上传（multipart `file`）→ `{imagePath:"/api/menu/images/{uuid}.ext"}` | item.manage |

图片 URL 归一化：响应中历史 `image_path` 若为 `/admin/plugins/menu-manager/items/images/...`，统一改写为 `/api/menu/images/...`，保证未登录可展示。

#### 4.4.3 打印机管理（复用 PrinterService / PrinterDriverRegistry）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/app/printers` | 打印机列表 | printer-core.config |
| GET | `/api/app/printers/drivers` | 可用驱动列表 | printer-core.config |
| POST | `/api/app/printers` | 新建打印机 | printer-core.config |
| PUT | `/api/app/printers/{id}` | 修改打印机 | printer-core.config |
| DELETE | `/api/app/printers/{id}` | 删除打印机 | printer-core.config |
| POST | `/api/app/printers/{id}/clear-queue` | 清空云端待打印队列 | printer-core.config |

#### 4.4.4 POS 点餐（复用 TableOrderService，请求/响应与 Web POS 对齐）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/app/pos/tables` | 桌台看板 | pos.view |
| GET | `/api/app/pos/catalog` | 点菜目录（含规格） | pos.view |
| GET | `/api/app/pos/confirmations` | 待确认顾客提交 | pos.view |
| POST | `/api/app/pos/confirmations/{id}/confirm` | 确认提交 | pos.confirm |
| POST | `/api/app/pos/confirmations/{id}/return` | 退回提交 | pos.confirm |
| GET | `/api/app/pos/sessions/{id}` | 会话详情 | pos.view |
| POST | `/api/app/pos/tables/{tableId}/open` | 开台 | pos.open |
| POST | `/api/app/pos/tables/{tableId}/share` | 拼台 | pos.open |
| POST | `/api/app/pos/sessions/{id}/items` | 加菜（含规格） | pos.view |
| POST | `/api/app/pos/sessions/{id}/batch-items` | 批量加菜 | pos.view |
| POST | `/api/app/pos/sessions/{id}/items/{itemId}/quantity` | 改数量 | pos.view |
| DELETE | `/api/app/pos/sessions/{id}/items/{itemId}` | 删菜 | pos.view |
| POST | `/api/app/pos/sessions/{id}/confirm` | 确认下单（触发后厨/打印流程） | pos.view |
| POST | `/api/app/pos/sessions/{id}/guest-count` | 改人数 | pos.open |
| POST | `/api/app/pos/sessions/{id}/combine` | 并台 | pos.open |
| POST | `/api/app/pos/sessions/{id}/transfer` | 转台 | pos.open |
| POST | `/api/app/pos/sessions/{id}/checkout` | 结账（现金） | pos.settle |
| POST | `/api/app/pos/tables/{tableId}/cleaning/complete` | 完成清台 | pos.open |

操作人取自 token 用户（`operator` 由后端填充，不信任客户端）。

#### 4.4.5 后厨（复用 KitchenQueueService，与 Web 后厨页对齐）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/app/kitchen/queue?status=&dispatchMode=&grouped=&page=` | 后厨队列 | kitchen.view |
| POST | `/api/app/kitchen/queue/{id}/start` | 开始制作 | kitchen.operate |
| POST | `/api/app/kitchen/queue/groups/start` | 批量开始 | kitchen.operate |
| POST | `/api/app/kitchen/queue/{id}/complete` | 制作完成 | kitchen.operate |
| POST | `/api/app/kitchen/production-batches/complete` | 按生产批次完成 | kitchen.operate |
| POST | `/api/app/kitchen/orders/{orderId}/print` | 手动打印订单 | kitchen.print |

#### 4.4.6 订单（复用 OrderService，与 Web 订单页对齐：查看为主）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/app/orders?status=&tableId=&dateFrom=&dateTo=` | 订单列表（默认当天） | order-core.admin.view |
| GET | `/api/app/orders/{id}` | 订单详情（含明细） | order-core.admin.view |

### 4.5 顾客侧 API 增强（scan-consumer / menu-manager）

#### 4.5.1 menu-manager 公开图片

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/menu/images/{fileName}` | 菜品图片公开读取（原有 `/admin/...` 上传接口保留） | 公开 |

#### 4.5.2 scan-consumer 增强（全部追加式）

| 类型 | 变更 | 说明 |
|------|------|------|
| 菜单响应 | `GET /consumer/goods/groups` 增加 `specGroups` | `[{id,name,required,multi,options:[{id,name,priceAdjust,formatPriceAdjust}]}]`；图片 URL 归一化为 `/api/menu/images/...` |
| 购物车 | `PUT /consumer/cart/goods/{goodsId}/count/{count}?tag_ids=` | `tag_ids` 传规格选项 ID（逗号分隔）时：校验必选/单选约束、按选项加价计算 `unit_price`、写入 `tag_names`；未传时行为不变 |
| 购物车响应 | `tags` 字段 | 返回规格描述（选项名），原有字段保留 |
| 下单 | `POST /consumer/orders/type/{orderType}` | 订单明细 `specification` 写规格描述（原为 tag_ids 原文），价格取购物车已含加价的单价；无规格时行为不变 |
| 门店/桌台 | `GET /consumer/stores` | 门店列表（App 绑定用，新增） |
| 门店/桌台 | `GET /consumer/stores/{storeId}/tables` | 桌台列表（App 绑定用，新增） |

规格解析逻辑（必选校验、单选校验、加价合计、描述拼接）当前在 order-table 的 `TableOrderService.resolveSpecification`（私有）。V1 在 scan-consumer 内实现同语义的私有解析器，不改动 order-table；后续如再出现第三处使用，再申请统一抽取。

### 4.6 数据库变更

新增表 `app_auth_token`（app-client V1）：

```sql
CREATE TABLE IF NOT EXISTS app_auth_token (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    token         VARCHAR(64)  NOT NULL,
    user_id       VARCHAR(64)  NOT NULL,
    username      VARCHAR(128) NOT NULL,
    display_name  VARCHAR(128),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    expires_at    DATETIME     NOT NULL,
    last_used_at  DATETIME,
    created_at    DATETIME     NOT NULL,
    UNIQUE KEY uk_app_auth_token (token),
    KEY idx_app_auth_token_user (user_id)
);
```

### 4.7 复用的现有服务

| 服务 | 来源插件 | 用途 |
|------|----------|------|
| `UserAccountService` / `PasswordEncoder` / `AuthorizationService` | platform（框架） | 员工登录、权限校验 |
| `CategoryService` / `MenuItemService` / `MenuItemImageStorageService` | menu-manager | 菜品管理、图片存取 |
| `PrinterService` / `PrinterDriverRegistry` | printer-core | 打印机管理 |
| `TableOrderService` | order-table | POS 点餐全流程 |
| `KitchenQueueService` | order-core | 后厨队列 |
| `OrderService` | order-core | 订单查询 |
| `ScanTableService` / `StoreService` / `ConsumerMenuService` / `CartService` / `ConsumerOrderService` / `ConsumerService` | scan-consumer | 顾客点餐 |

## 5. App 端设计

### 5.1 技术选型

- uni-app（Vue 3 + TypeScript + Vite），一套代码编译 Android App / iOS / H5
- 状态管理 Pinia；请求层基于 `uni.request` 封装（统一 baseURL、token、`{code,msg,data}` 解包、401 自动跳登录）
- 平板优先布局（大字号、左右分栏 POS 风格），手机端自适应
- 开发调试用 H5（浏览器），打包用 HBuilderX（已具备）

### 5.2 模式与页面

App 启动默认进入**顾客模式**；顶部提供“员工入口”，登录后进入员工模式，可切换回顾客模式。

顾客模式：

| 页面 | 内容 |
|------|------|
| 绑定/进店 | 选择门店与桌台（或扫描桌台二维码），本地持久化 |
| 菜单 | 左侧分类、右侧菜品卡片（图片、价格、加减），支持规格选配弹窗 |
| 购物车/下单 | 明细（含规格）、数量、备注、提交订单 |
| 订单 | 当前未结订单、历史订单、订单详情 |

员工模式：

| 页面 | 内容 |
|------|------|
| 登录 | 账号密码（JNimble 后台账号） |
| POS 桌台 | 桌台看板（区域/状态/人数），开台、拼台、转台、并台、清台 |
| POS 点菜 | 目录（含规格选配）、会话购物车、确认下单、待确认提交处理、结账（现金/找零） |
| 后厨制作 | 队列（按状态/模式过滤、分组）、开始制作、完成、手动打印 |
| 订单 | 当天订单列表（按状态过滤）、订单详情（明细/金额/状态） |
| 菜品管理 | 分类管理、菜品列表、编辑（图片上传、规格组、上下架、必点） |
| 打印机管理 | 列表、新增/编辑（驱动、类型、配置、启停）、删除、清队列 |
| 设置 | 服务器地址、模式切换、退出登录 |

### 5.3 App 仓库结构（`../jnimble-order-app`）

```
jnimble-order-app/
├── src/
│   ├── api/          # auth.ts / menu.ts / printer.ts / pos.ts / kitchen.ts / order.ts / customer.ts
│   ├── pages/
│   │   ├── customer/ # 顾客模式页面
│   │   ├── staff/    # 员工模式页面
│   │   └── common/   # 登录、设置
│   ├── stores/       # pinia（session / table-binding / cart）
│   ├── components/
│   └── utils/        # request 封装、常量
├── package.json
└── README.md         # 开发与打包说明
```

## 6. 分期计划

| 阶段 | 内容 | 验收方式 |
|------|------|----------|
| P1 | 后端：menu-manager 公开图片；scan-consumer 规格/图片/门店桌台增强；app-client 骨架 + 鉴权 | 插件单测 + curl 冒烟 |
| P2 | 后端：app-client 菜品管理、打印机管理、POS、后厨、订单 | 插件单测 + curl 冒烟（开台→点菜→确认→后厨→结账全流程） |
| P3 | App：项目骨架、请求层、登录/设置、顾客模式（含规格） | H5 浏览器联调 |
| P4 | App：员工模式（POS、后厨、订单、菜品管理、打印机管理） | H5 + 平板浏览器联调 |
| P5 | 真机打包（HBuilderX → APK）、联调修复、文档 | 平板/手机安装验证 |

## 7. 已确认事项

1. 顾客模式支持规格选配（线上扫码点餐同步获得该能力，向后兼容）。
2. V1 包含后厨制作页与订单页（查看/操作对齐 Web 后台）。
3. 结账沿用现金/线下，不做在线支付。
4. App 仓库与本仓库同级（`../jnimble-order-app`），打包使用 HBuilderX。
5. 员工 token 有效期 30 天。

## 8. 实现记录与已知问题

### 8.1 失败响应处理方式（绕开框架限制）

框架 `PluginHandlerExceptionResolver` 为插件子上下文创建的 `ExceptionHandlerExceptionResolver` 未配置消息转换器，
导致插件内 `@RestControllerAdvice` 无法写出 JSON 响应（抛 `HttpMediaTypeNotAcceptableException`，前端得到 Spring 默认 500）。
app-client 采用与 scan-consumer 相同的模式：控制器内统一通过 `AppApiExecutor.guard(...)` 捕获异常并返回
`{code,msg,data}`，HTTP 状态码（401/403/400/409/500）通过 `HttpServletResponse.setStatus` 设置。

建议（后续可优化）：框架给 `PluginHandlerExceptionResolver` 注入主上下文的消息转换器，使插件 `@ControllerAdvice` 正常工作。

### 8.2 冒烟验证结果（本地实例，2026-10-08）

已通过 curl 验证：
- 员工登录/鉴权（成功登录、错误密码 401、无 token 401）
- 菜品管理（分类/菜品 CRUD、规格组保存、图片上传 + 公开读取）
- 打印机管理（列表/驱动/新增/修改/删除）
- POS 点餐全流程（开台 → 规格加菜 → 确认下单 → 后厨开始/完成 → 现金结账 → 清台）
- 后厨队列查询与操作、订单列表/详情
- 顾客点餐（规格选配加价、购物车规格描述、下单、订单详情）
- 门店/桌台列表（App 绑定用）

### 8.3 App 端实现记录（jnimble-order-app）

页面（UniAPP Vue3 + TypeScript + Vite + Pinia）：
- 顾客模式：`customer/bind`（门店/桌台绑定、扫码）、`customer/menu`（菜单 + 规格弹层 + 购物车栏）、`customer/cart`（下单）、`customer/orders`、`customer/order-detail`
- 员工模式：`staff/login`、`staff/home`（按权限显示入口）、`staff/pos/tables`（桌台看板 + 待确认提交）、`staff/pos/session`（点菜/规格/确认/现金结账/清台）、`staff/kitchen`、`staff/orders`、`staff/order-detail`、`staff/menu/list`、`staff/menu/edit`（含规格组与图片上传）、`staff/printers`
- 通用：`settings`（服务器地址、绑定信息、账号）

本地存储键：`jnimble.serverBase`（服务器地址）、`jnimble.customer`（桌台绑定与设备令牌）、`jnimble.staff`（员工 token/权限）。

联调中补充：购物车响应增加 `tagIds` 字段（App 精确加减规格行需要），见 `ConsumerController.getCart`。

浏览器（H5）联调已通过：顾客规格加购（20+2=22）、下单合并到未结订单、员工登录、开台（人数超限正确报错）、点菜（规格加价 20+1=21）、确认下单、现金结账找零（实收 50/应收 21/找零 29）、完成清台、后厨开始制作、菜品编辑保存、打印机新增/删除、订单列表。

### 8.4 端到端测试记录（2026-10-08，App UI + API 双验证）

| 场景 | 步骤 | 结果 |
|------|------|------|
| 顾客首单 | 绑定新桌台（E2E-A2）→ 加「冒烟测试菜(微辣)x2 + 宫保鸡丁x1」→ 提交 | 新订单 D202610080004（SCAN/CONFIRMED/¥80），明细规格与单价正确，后厨入队 |
| 顾客加菜 | 同桌再加「宫保鸡丁x1」→ 提交 | 合并进同一订单（¥118），新增 batchSeq=2 明细，后厨新增队列项 |
| 员工 POS | 开台（R1267293/2人）→ 加「冒烟测试菜(特辣)+宫保鸡丁」→ 确认下单 → 后厨开始/完成 → 现金结账（收100/应收60/找零40）→ 完成清台 | 订单 D202610080005（POS/SETTLED/¥60），队列项 COMPLETED，桌台恢复 FREE |
| 结账弹窗文案 | 再次开台点单结账（应收38/收50/找零12） | 弹窗显示「应收/实收现金/找零」正确（修复后经 HMR 验证） |
| 管理 | 菜品编辑保存（sortOrder 98→97，规格/图片保留）、打印机增删 | 通过 |

**测试中发现并修复的问题**：
1. `ConsumerOrderService.getUnpaidOrder` 未按桌台过滤，导致多桌场景下「未结订单」串桌、且新桌首次下单可能误判存在未结单而失败。已修复（加 `table_id` 过滤）并验证：E2E-A2 与 A1 未结订单互不干扰。

**已知限制（与现有线上点餐一致，未在本次修改）**：
1. 顾客下单的「备注」参数后端未持久化（`ConsumerOrderService.createOrder` 收到 `note` 但未写入订单）；如需支持需 order-core 增加更新订单备注的能力。
2. POS「待确认提交」区域当前无数据源：order-core 的提交暂存（`OrderSubmissionService.submitInitial/submitItems`）暂无调用方，属预留能力。

> 2026-10-08 的 E2E 首单/加菜订单（D202610080004，¥118 未结）为测试数据，保留在本地环境。


