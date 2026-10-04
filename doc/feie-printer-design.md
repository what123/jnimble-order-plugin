# 飞鹅云打印插件设计文档

本文档描述 `jnimble-plugin-printer-feie` 的设计与实现,以及它如何与 `jnimble-plugin-printer-core` 的自定义模板系统对接。

## 背景

JNimble 打印体系分两层:

- `jnimble-plugin-printer-core`:模板渲染 + 任务调度(driver 无关)
- `jnimble-plugin-printer-feie`:飞鹅云打印 driver 实现

之前的飞鹅 driver 是桩实现(`print()` 只打日志,直接返回 success),并且**没有调度器**把 PENDING 任务拉起来调 driver,导致自定义模板渲染完成后无法真正下发到打印机。本次改造补齐了从"渲染产物 → 飞鹅云 API"的完整链路。

## 整体链路

```
订单确认事件
  │
  ▼
PrinterKitchenPrintGateway.print()              (printer-core)
  │  拉模板、构造数据
  ▼
PrintTemplateRenderService.renderPublished()    (printer-core)
  │  渲染成 jnimble.print-document.v1 JSON
  ▼
PrintJobService.createDocumentJob()             (printer-core)
  │  入库 prn_print_job,status=PENDING
  ▼
【异步】PrintJobDispatcher.dispatch()            (printer-core, @Scheduled)
  │  拉取 PENDING 任务 → 解析打印机 config → 找 driver
  ▼
FeiePrinterDriver.print(task, config)           (printer-feie)
  │  task.content() 是 JSON document
  ▼
FeieEscPosRenderer.render(json)                 (printer-core,共享)
  │  JSON document → 飞鹅 ESC/POS 文本
  ▼
FeieApiClient.printMessage(sn, escpos, copies)  (printer-feie)
  │  SHA1 签名 → POST https://api.feieyun.cn/Api/Open/
  ▼
返回 ret=0 → PrintJobService.markSuccess(jobId, feieOrderId)
返回 ret≠0 或异常 → PrintJobService.markFailed(jobId, msg)
```

## 关键组件

### 1. `FeieEscPosRenderer`(printer-core,共享)

位置:`template/FeieEscPosRenderer.java`

把 `jnimble.print-document.v1` JSON 转成飞鹅云可识别的 ESC/POS 文本。支持以下 element kind:

| kind | 转换规则 |
|---|---|
| `TEXT` | `size=LARGE` → `<C double-height>...</C>`;`bold=true` → `<BOLD>...</BOLD>`;`align=CENTER` → `<CENTER>...</CENTER>`;其他原样输出 |
| `TABLE` | 输出表头行 + 数据行,列宽按 `widthBasisPoints` 比例分配到纸张字符宽度(58mm=32 字符,80mm=48 字符);`showHeader=true` 时含分隔行 `---` |
| `AMOUNT_ROWS` | 每行 `label   value` 两端对齐,`bold=true` 时整行包 `<BOLD>` |
| `QR` | `<QR>payload</QR>` + 可选 `<CENTER>caption</CENTER>`;`hidden=true` 时跳过 |
| 未知 kind | 静默跳过(向前兼容未来扩展的 block 类型) |

XML 特殊字符会转义(`<` → `&lt;`、`>` → `&gt;`),避免与飞鹅标签冲突。

### 2. `FeieApiClient`(printer-feie)

位置:`feie/FeieApiClient.java`

基于 JDK 21 自带的 `java.net.http.HttpClient`,零外部依赖。封装飞鹅云全部 9 个主动调用接口:

| 方法 | 飞鹅 API | 用途 |
|---|---|---|
| `printMessage(sn, content, copies)` | `Open_printMsg` | 小票机打印订单 |
| `printLabelMessage(sn, content, copies, img)` | `Open_printLabelMsg` | 标签机打印(标签机专用) |
| `queryPrinterStatus(sn)` | `Open_queryPrinterStatus` | 查询打印机在线/离线 |
| `queryOrderState(orderId)` | `Open_queryOrderState` | 查询订单是否打印成功 |
| `queryOrderInfoByDate(sn, date)` | `Open_queryOrderInfoByDate` | 查询指定打印机某天订单统计 |
| `printerAddList(printerContent)` | `Open_printerAddList` | 批量添加打印机 |
| `printerDelList(snList)` | `Open_printerDelList` | 批量删除打印机 |
| `printerEdit(sn, name, phoneNum)` | `Open_printerEdit` | 修改打印机信息 |
| `delPrinterSQS(sn)` | `Open_delPrinterSQS` | 清空待打印队列 |

签名:`sig = SHA1(user + ukey + stime)`(小写十六进制),POST `application/x-www-form-urlencoded` 到 `https://api.feieyun.cn/Api/Open/`。

返回值为飞鹅原始 JSON(`{ret, msg, data, serverExecutedTime}`),driver / controller 负责业务语义判断。

### 3. `FeiePrinterDriver`(printer-feie)

位置:`feie/FeiePrinterDriver.java`

实现 `PrinterDriver` 接口,从 `PrinterConfig.properties()` 读取:

| 属性 | 说明 |
|---|---|
| `user` | 飞鹅账号(必填) |
| `ukey` | 飞鹅 UKEY(必填) |
| `sn`  | 打印机编号(必填,每台打印机独立) |

`print(task, config)` 行为:

1. 校验 `sn` 存在
2. 根据 `task.contentType()`:
   - `application/vnd.jnimble.print-document+json` → 走 `FeieEscPosRenderer.render()` 转 ESC/POS
   - 其他(text/plain 等)→ 透传(兼容直接发送 ESC/POS 文本的场景)
3. 调 `client.printMessage(sn, escpos, copies)`
4. `ret == 0` → `PrintResult(true, data, msg)`,把飞鹅返回的订单 ID 作为 `jobId` 回写
5. `ret != 0` → `PrintResult(false, orderId, msg)`
6. API 异常 → `PrintResult(false, orderId, errorMessage)`,不向上传播

`queryStatus(config)` 行为:调 `queryPrinterStatus`,`data` 含"离线"返回 `OFFLINE`,含"在线"返回 `ONLINE`,其他返回 `ERROR`。

### 4. `FeiePluginBoot`(printer-feie)

位置:`feie/FeiePluginBoot.java`

插件入口,从系统属性/环境变量读取飞鹅账号级配置:

| 配置来源 | 优先级 |
|---|---|
| `-Djnimble.plugin.feie.user=xxx` | 最高 |
| 环境变量 `JNIMBLE_FEIE_USER` | 次之 |
| 默认空字符串 | 兜底(测试环境用) |

`ukey` 同理。每台打印机的 `sn` 在后台"打印机管理"页面里配置(存到 `prn_printer.config_json` JSON 字段)。

### 5. `PrintJobDispatcher`(printer-core)

位置:`service/PrintJobDispatcher.java`

Spring `@Scheduled` 调度器,默认每 5 秒拉一次 `PENDING` 任务。可配置:

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `jnimble.printer.dispatcher.enabled` | `true` | 总开关 |
| `jnimble.printer.dispatcher.interval-ms` | `5000` | 调度间隔(毫秒) |
| `jnimble.printer.dispatcher.batch-size` | `20` | 每轮最多处理任务数 |

每轮逻辑:

1. `printJobService.listPendingJobs(batchSize)` 拉任务
2. 对每个任务:
   - 取 `printerId` → 查 `PrinterEntity` → 不存在或 disabled → `markFailed`
   - `driverRegistry.resolve(printer.driverId)` → 找不到 driver → `markFailed`
   - `driver.supports(config)` → false → `markFailed`
   - `driver.print(task, config)`:
     - 成功 → `markSuccess(jobId, externalOrderId)`,回写飞鹅订单 ID
     - 失败 → `markFailed(jobId, errorMessage)`,根据 `retryCount/maxRetries` 决定是否重试
3. 异常一律捕获,不向上传播,避免调度线程崩溃

### 6. `PrintJobService` 新增方法

- `listPendingJobs(int limit)`:按 `created_at` 升序拉 PENDING 任务
- `markSuccess(String jobId, String externalOrderId)`:标记成功 + 回写 `external_order_id` + `printed_at`
- `markFailed(String jobId, String errorMessage)`:根据 `retryCount/maxRetries` 决定保持 `PENDING` 重试还是转 `FAILED`

### 7. 数据库迁移 V7

`db/migration/plugin/printer-core/V7__add_print_job_external_order_id.sql` 给 `prn_print_job` 表加 `external_order_id` 字段,存飞鹅返回的订单 ID,便于后续 `Open_queryOrderState` 查询打印结果。

### 8. `PrinterDriver` SPI 扩展点

`spi/PrinterDriver.java` 加了两个 default 方法,driver 按需 override:

| 方法 | 默认行为 | 飞鹅 driver 实现 |
|---|---|---|
| `clearPendingQueue(config)` | `unsupported` | 调 `Open_delPrinterSQS`,返回 `PrinterOperationResult` |
| `queryOrderStatistics(config, date)` | `unsupported` | 调 `Open_queryOrderInfoByDate`,返回 `PrinterOrderStatistics` |

返回类型:
- `PrinterOperationResult(success, driverId, message)`:通用操作结果
- `PrinterOrderStatistics(supported, printed, waiting, message)`:统计结果,`supported=false` 表示 driver 不支持

### 9. `FeieAdminController`(printer-feie)

位置:`feie/FeieAdminController.java`,URL 前缀 `/admin/plugins/printer-feie/`,权限 `printer-feie.config`。

暴露飞鹅特有的管理端点(通用能力走 printer-core controller + SPI):

| 端点 | 飞鹅 API | 用途 |
|---|---|---|
| `POST /printers/batch-add` | `Open_printerAddList` | 批量添加飞鹅打印机,返回 `ok` 数组自动同步到本地 `prn_printer` 表(driverId=feie, enabled=false) |
| `POST /printers/batch-delete` | `Open_printerDelList` | 批量删除飞鹅云打印机(本地数据库不自动删除) |
| `POST /printers/edit` | `Open_printerEdit` | 修改飞鹅云打印机备注名/流量卡号码 |
| `POST /label-print` | `Open_printLabelMsg` | 标签机打印(标签机专用) |

### 10. `PrinterAdminController` 通用端点扩展

printer-core 通用 controller 加了两个端点(走 driver SPI,不绑定飞鹅):

| 端点 | SPI 方法 | 用途 |
|---|---|---|
| `POST /printers/{id}/clear-queue` | `clearPendingQueue(config)` | 清空云端待打印队列 |
| `GET /printers/{id}/order-statistics?date=yyyy-MM-dd` | `queryOrderStatistics(config, date)` | 查询某天订单统计 |

### 11. 后台管理页面增强

- `printers.html`:新增"批量添加(飞鹅)"按钮 + 弹窗,提交到 `printer-feie/printers/batch-add`,自动同步成功的打印机到本地表;每行飞鹅打印机多一个"清空队列"按钮(仅 driverId=feie 显示)
- `jobs.html`:新增折叠面板"订单统计",选择本地打印机 + 日期,调 `printer-core/printers/{id}/order-statistics`,driver 转发到飞鹅云

## 配置示例

### 飞鹅账号级(启动参数)

```bash
java -Djnimble.plugin.feie.user=your-feie-user \
     -Djnimble.plugin.feie.ukey=your-feie-ukey \
     -jar jnimble-starter.jar
```

或环境变量:

```bash
export JNIMBLE_FEIE_USER=your-feie-user
export JNIMBLE_FEIE_UKEY=your-feie-ukey
```

### 打印机级(后台配置)

在"打印机管理"页面新增打印机,`driver_id=feie`,`config_json` 填:

```json
{
  "user": "your-feie-user",
  "ukey": "your-feie-ukey",
  "sn": "316500011"
}
```

> 注:如果打印机 `config_json` 里有 `user/ukey`,优先用打印机级配置;否则用账号级(系统属性/环境变量)。当前实现读打印机级,后续可扩展为 fallback 到账号级。

## 测试覆盖

| 测试类 | 用例数 | 覆盖范围 |
|---|---|---|
| `FeieEscPosRendererTest` | 12 | TEXT/TABLE/AMOUNT_ROWS/QR 转换、HTML 转义、未知 block 跳过、空输入 |
| `FeiePrinterDriverTest` | 25 | supports/print/queryStatus/clearPendingQueue/queryOrderStatistics/printLabel 各分支、ret=0/非 0/异常、纯文本透传、3 个 raw 方法透传 |
| `FeiePluginBootTest` | 3 | boot 注册 driver、driverId=feie |
| `PrintJobDispatcherTest` | 10 | 各种失败分支、成功路径、config_json 解析 |

跑测试:

```bash
mvn -f plugins/pom.xml test -pl jnimble-plugin-printer-core,jnimble-plugin-printer-feie -am -Dcheckstyle.skip=true -Dspotbugs.skip=true
```

## 飞鹅云 API 接口清单

飞鹅云打印共 9 个主动调用接口,本插件已**全部实现**:

| 接口 | 状态 | 调用位置 |
|---|---|---|
| `Open_printMsg` | ✅ | `FeiePrinterDriver.print()` |
| `Open_printLabelMsg` | ✅ | `FeiePrinterDriver.printLabel()`(由 `FeieAdminController.labelPrint` 触发) |
| `Open_queryPrinterStatus` | ✅ | `FeiePrinterDriver.queryStatus()` |
| `Open_queryOrderState` | ✅ | `FeieApiClient.queryOrderState()`(driver 未直接调用,留作扩展) |
| `Open_queryOrderInfoByDate` | ✅ | `FeiePrinterDriver.queryOrderStatistics()`(由 `PrinterAdminController.printerOrderStatistics` 触发) |
| `Open_printerAddList` | ✅ | `FeiePrinterDriver.addPrintersRaw()`(由 `FeieAdminController.batchAddPrinters` 触发) |
| `Open_printerDelList` | ✅ | `FeiePrinterDriver.deletePrintersRaw()`(由 `FeieAdminController.batchDeletePrinters` 触发) |
| `Open_printerEdit` | ✅ | `FeiePrinterDriver.editPrinterRaw()`(由 `FeieAdminController.editPrinter` 触发) |
| `Open_delPrinterSQS` | ✅ | `FeiePrinterDriver.clearPendingQueue()`(由 `PrinterAdminController.clearPrinterQueue` 触发) |
