package com.jnimble.plugin.printer.feie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.printer.feie.FeieApiClient.FeieApiException;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import com.jnimble.plugin.printer.service.PrinterService;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterDriver;
import com.jnimble.plugin.printer.spi.PrinterDriverRegistry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 飞鹅云特有管理端点。
 *
 * <p>暴露飞鹅云专属能力(批量增删打印机、修改打印机信息、标签机打印)。
 * 通用打印能力(单条 CRUD、查询状态、清空队列、订单统计)走 printer-core 通用 controller,
 * 由 driver SPI 转发到飞鹅。</p>
 *
 * <p>所有端点要求 {@code printer-feie.config} 权限。</p>
 */
@Controller
@RequestMapping("/admin/plugins/printer-feie")
public class FeieAdminController {

    private final PrinterDriverRegistry driverRegistry;
    private final PrinterService printerService;
    private final ObjectMapper objectMapper;
    private final ControllerAuthorization authorization;

    public FeieAdminController(
            PrinterDriverRegistry driverRegistry,
            PrinterService printerService,
            ObjectMapper objectMapper,
            ControllerAuthorization authorization
    ) {
        this.driverRegistry = driverRegistry;
        this.printerService = printerService;
        this.objectMapper = objectMapper;
        this.authorization = authorization;
    }

    /**
     * 批量添加飞鹅打印机到飞鹅云,并按返回结果同步到本地 {@code prn_printer} 表。
     *
     * <p>请求体格式:</p>
     * <pre>{@code
     * {
     *   "printers": "sn1#key1#备注1#carnum1\nsn2#key2#备注2#"
     * }
     * }</pre>
     *
     * <p>返回 {@code ok} 数组中成功的打印机会自动写入本地数据库(状态 enabled=false,
     * driverId=feie,configJson 含 user/ukey/sn)。{@code no} 数组保留飞鹅原始错误描述。</p>
     */
    @PostMapping("/printers/batch-add")
    @ResponseBody
    public Map<String, Object> batchAddPrinters(@RequestBody Map<String, String> request) {
        authorization.requirePermission("printer-feie.config");
        String printerContent = request.get("printers");
        if (printerContent == null || printerContent.isBlank()) {
            throw new IllegalArgumentException("printers field is required");
        }
        FeiePrinterDriver driver = resolveFeieDriver();
        JsonNode response = driver.addPrintersRaw(printerContent);
        return handleBatchAddResponse(response);
    }

    /**
     * 批量删除飞鹅云打印机。本地数据库对应记录不自动删除(避免误删),需另行操作。
     *
     * <p>请求体格式:</p>
     * <pre>{@code
     * { "snlist": "sn1-sn2-sn3" }
     * }</pre>
     */
    @PostMapping("/printers/batch-delete")
    @ResponseBody
    public Map<String, Object> batchDeletePrinters(@RequestBody Map<String, String> request) {
        authorization.requirePermission("printer-feie.config");
        String snlist = request.get("snlist");
        if (snlist == null || snlist.isBlank()) {
            throw new IllegalArgumentException("snlist field is required");
        }
        FeiePrinterDriver driver = resolveFeieDriver();
        JsonNode response = driver.deletePrintersRaw(snlist);
        return toResponseMap(response);
    }

    /**
     * 修改飞鹅云打印机信息(备注名 / 流量卡号码)。
     *
     * <p>请求体格式:</p>
     * <pre>{@code
     * { "sn": "316500011", "name": "前台1号", "phonenum": "13800001111" }
     * }</pre>
     */
    @PostMapping("/printers/edit")
    @ResponseBody
    public Map<String, Object> editPrinter(@RequestBody Map<String, String> request) {
        authorization.requirePermission("printer-feie.config");
        String sn = request.get("sn");
        String name = request.get("name");
        if (sn == null || sn.isBlank() || name == null) {
            throw new IllegalArgumentException("sn and name are required");
        }
        FeiePrinterDriver driver = resolveFeieDriver();
        JsonNode response = driver.editPrinterRaw(sn, name, request.get("phonenum"));
        return toResponseMap(response);
    }

    /**
     * 标签机打印(标签机专用接口,小票机不能用)。
     *
     * <p>请求体格式:</p>
     * <pre>{@code
     * {
     *   "printerId": "local-printer-id",
     *   "orderId": "order-xxx",
     *   "content": "<CB>标签内容</CB>",
     *   "copies": 1,
     *   "imageBase64": ""
     * }
     * }</pre>
     */
    @PostMapping("/label-print")
    @ResponseBody
    public Map<String, Object> labelPrint(@RequestBody Map<String, Object> request) {
        authorization.requirePermission("printer-feie.config");
        String printerId = (String) request.get("printerId");
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId is required");
        }
        PrinterEntity printer = printerService.getPrinter(printerId);
        if (printer == null) {
            throw new IllegalArgumentException("Printer not found: " + printerId);
        }
        FeiePrinterDriver driver = resolveFeieDriver();
        PrinterConfig config = buildConfig(printer);
        if (!driver.supports(config)) {
            throw new IllegalStateException("Driver does not support printer config: " + printerId);
        }
        String content = String.valueOf(request.getOrDefault("content", ""));
        int copies = asInt(request.get("copies"), 1);
        com.jnimble.plugin.printer.spi.PrintTask task = new com.jnimble.plugin.printer.spi.PrintTask(
                String.valueOf(request.getOrDefault("orderId", "")),
                content,
                "text/plain; charset=UTF-8",
                "PLAIN",
                "LABEL",
                copies
        );
        String imageBase64 = (String) request.get("imageBase64");
        com.jnimble.plugin.printer.spi.PrintResult result = driver.printLabel(task, config, imageBase64);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("feieOrderId", result.jobId());
        response.put("message", result.message());
        return response;
    }

    private FeiePrinterDriver resolveFeieDriver() {
        PrinterDriver driver = driverRegistry.resolve("feie");
        if (!(driver instanceof FeiePrinterDriver feieDriver)) {
            throw new IllegalStateException("Feie driver not registered or wrong type: " + driver.getClass());
        }
        return feieDriver;
    }

    private PrinterConfig buildConfig(PrinterEntity printer) {
        Map<String, String> properties = new HashMap<>();
        String configJson = printer.getConfigJson();
        if (configJson != null && !configJson.isBlank()) {
            try {
                var parsed = objectMapper.readTree(configJson);
                parsed.fields().forEachRemaining(entry -> {
                    var value = entry.getValue();
                    if (value != null && !value.isNull()) {
                        properties.put(entry.getKey(), value.asText());
                    }
                });
            } catch (Exception ignored) {
                // 解析失败留空 properties,supports 会返回 false
            }
        }
        return new PrinterConfig(printer.getDriverId(), properties);
    }

    private Map<String, Object> handleBatchAddResponse(JsonNode response) {
        Map<String, Object> result = new LinkedHashMap<>();
        int ret = response.path("ret").asInt(-1);
        result.put("ret", ret);
        result.put("msg", response.path("msg").asText(""));
        if (ret == 0) {
            JsonNode data = response.path("data");
            persistAddedPrinters(data.path("ok"));
            result.put("ok", data.path("ok"));
            result.put("no", data.path("no"));
        }
        return result;
    }

    private void persistAddedPrinters(JsonNode okArray) {
        if (okArray == null || !okArray.isArray()) {
            return;
        }
        for (JsonNode entry : okArray) {
            String[] parts = entry.asText("").split("#");
            if (parts.length < 2) {
                continue;
            }
            String sn = parts[0].trim();
            String key = parts[1].trim();
            String remark = parts.length > 2 ? parts[2].trim() : "";
            PrinterEntity entity = new PrinterEntity();
            entity.setId("feie-" + sn);
            entity.setName(remark.isEmpty() ? sn : remark);
            entity.setDriverId("feie");
            entity.setType("TICKET");
            entity.setEnabled(Boolean.FALSE);
            entity.setConfigJson(buildConfigJson(sn, key));
            try {
                PrinterEntity existing = printerService.getPrinter(entity.getId());
                if (existing == null) {
                    printerService.createPrinter(entity);
                } else {
                    entity.setUpdatedAt(java.time.LocalDateTime.now());
                    printerService.updatePrinter(entity);
                }
            } catch (Exception ignored) {
                // 单条失败不阻断整体流程
            }
        }
    }

    private String buildConfigJson(String sn, String key) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("sn", sn);
        config.put("key", key);
        return config.toString();
    }

    private Map<String, Object> toResponseMap(JsonNode response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ret", response.path("ret").asInt(-1));
        result.put("msg", response.path("msg").asText(""));
        JsonNode data = response.path("data");
        if (!data.isMissingNode() && !data.isNull()) {
            result.put("data", data);
        }
        return result;
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return defaultValue;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", "BAD_REQUEST");
        result.put("message", ex.getMessage());
        return result;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public Map<String, Object> handleIllegalState(IllegalStateException ex) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", "CONFLICT");
        result.put("message", ex.getMessage());
        return result;
    }

    @ExceptionHandler(FeieApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ResponseBody
    public Map<String, Object> handleFeieApi(FeieApiException ex) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", "FEIE_API_ERROR");
        result.put("message", ex.getMessage());
        return result;
    }

    @ExceptionHandler({Exception.class})
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", "INTERNAL_ERROR");
        result.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
