package com.jnimble.plugin.printer.feie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.printer.feie.FeieApiClient.FeieApiException;
import com.jnimble.plugin.printer.spi.PrinterOperationResult;
import com.jnimble.plugin.printer.spi.PrinterOrderStatistics;
import com.jnimble.plugin.printer.spi.PrintResult;
import com.jnimble.plugin.printer.spi.PrintTask;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterDriver;
import com.jnimble.plugin.printer.spi.PrinterStatus;
import com.jnimble.plugin.printer.template.FeieEscPosRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 飞鹅云打印 driver。
 *
 * <p>从 {@link PrinterConfig#properties()} 读取:</p>
 * <ul>
 *   <li>{@code user} —— 飞鹅账号(必填)</li>
 *   <li>{@code ukey} —— 飞鹅 UKEY(必填)</li>
 *   <li>{@code sn}  —— 打印机编号(必填)</li>
 * </ul>
 *
 * <p>{@link #print(PrintTask, PrinterConfig)} 把 {@link PrintTask#content()} 当作
 * {@code jnimble.print-document.v1} JSON,通过 {@link FeieEscPosRenderer} 转成
 * ESC/POS 文本后调 {@link FeieApiClient#printMessage(String, String, int)}。</p>
 */
public class FeiePrinterDriver implements PrinterDriver {

    private static final Logger log = LoggerFactory.getLogger(FeiePrinterDriver.class);

    private static final String PROP_USER = "user";
    private static final String PROP_UKEY = "ukey";
    private static final String PROP_SN = "sn";

    private static final String CONTENT_TYPE_PRINT_DOCUMENT = "application/vnd.jnimble.print-document+json";

    private final FeieApiClient client;
    private final FeieEscPosRenderer renderer;

    public FeiePrinterDriver(FeieApiClient client, FeieEscPosRenderer renderer) {
        this.client = client;
        this.renderer = renderer;
    }

    @Override
    public String driverId() {
        return "feie";
    }

    @Override
    public String driverName() {
        return "飞鹅云打印";
    }

    @Override
    public boolean supports(PrinterConfig config) {
        return "feie".equals(config.driverId())
                && required(config, PROP_USER) != null
                && required(config, PROP_UKEY) != null
                && required(config, PROP_SN) != null;
    }

    @Override
    public PrintResult print(PrintTask task, PrinterConfig config) {
        String sn = required(config, PROP_SN);
        if (sn == null) {
            return new PrintResult(false, task.orderId(), "Feie printer SN is missing");
        }
        String content = renderContent(task);
        int copies = Math.max(1, task.copies());
        try {
            JsonNode response = client.printMessage(sn, content, copies);
            return toPrintResult(task, response);
        } catch (FeieApiException ex) {
            log.error("Feie print failed: orderId={}, sn={}", task.orderId(), sn, ex);
            return new PrintResult(false, task.orderId(), "Feie API error: " + ex.getMessage());
        }
    }

    private PrintResult toPrintResult(PrintTask task, JsonNode response) {
        int ret = response.path("ret").asInt(-1);
        String msg = response.path("msg").asText("");
        String data = response.path("data").asText("");
        if (ret == 0) {
            log.info("Feie print submitted: orderId={}, feieOrderId={}", task.orderId(), data);
            return new PrintResult(true, data, "Feie accepted: " + msg);
        }
        log.warn("Feie print rejected: orderId={}, ret={}, msg={}", task.orderId(), ret, msg);
        return new PrintResult(false, task.orderId(), "Feie rejected (" + ret + "): " + msg);
    }

    private PrinterOperationResult toOperationResult(JsonNode response) {
        int ret = response.path("ret").asInt(-1);
        String msg = response.path("msg").asText("");
        if (ret == 0) {
            return PrinterOperationResult.success(driverId(), "Feie ok: " + msg);
        }
        return PrinterOperationResult.failure(driverId(), "Feie rejected (" + ret + "): " + msg);
    }

    @Override
    public PrinterStatus queryStatus(PrinterConfig config) {
        String sn = required(config, PROP_SN);
        if (sn == null) {
            return PrinterStatus.ERROR;
        }
        try {
            JsonNode response = client.queryPrinterStatus(sn);
            int ret = response.path("ret").asInt(-1);
            if (ret != 0) {
                return PrinterStatus.ERROR;
            }
            String data = response.path("data").asText("").toLowerCase();
            if (data.contains("离线") || data.contains("offline")) {
                return PrinterStatus.OFFLINE;
            }
            return PrinterStatus.ONLINE;
        } catch (FeieApiException ex) {
            log.error("Feie query status failed: sn={}", sn, ex);
            return PrinterStatus.ERROR;
        }
    }

    @Override
    public PrinterOperationResult clearPendingQueue(PrinterConfig config) {
        String sn = required(config, PROP_SN);
        if (sn == null) {
            return PrinterOperationResult.failure(driverId(), "Feie printer SN is missing");
        }
        try {
            JsonNode response = client.delPrinterSQS(sn);
            return toOperationResult(response);
        } catch (FeieApiException ex) {
            log.error("Feie clear pending queue failed: sn={}", sn, ex);
            return PrinterOperationResult.failure(driverId(), "Feie API error: " + ex.getMessage());
        }
    }

    @Override
    public PrinterOrderStatistics queryOrderStatistics(PrinterConfig config, String date) {
        String sn = required(config, PROP_SN);
        if (sn == null) {
            return PrinterOrderStatistics.unsupported(driverId());
        }
        try {
            JsonNode response = client.queryOrderInfoByDate(sn, date);
            int ret = response.path("ret").asInt(-1);
            if (ret != 0) {
                return PrinterOrderStatistics.unsupported(driverId());
            }
            JsonNode data = response.path("data");
            long printed = data.path("printed").asLong(-1);
            long waiting = data.path("waiting").asLong(-1);
            return PrinterOrderStatistics.of(printed, waiting, response.path("msg").asText(""));
        } catch (FeieApiException ex) {
            log.error("Feie query order statistics failed: sn={}, date={}", sn, date, ex);
            return PrinterOrderStatistics.unsupported(driverId());
        }
    }

    /**
     * 飞鹅特有:标签机打印(标签机专用,不能用于小票机)。
     */
    public PrintResult printLabel(PrintTask task, PrinterConfig config, String imageBase64) {
        String sn = required(config, PROP_SN);
        if (sn == null) {
            return new PrintResult(false, task.orderId(), "Feie printer SN is missing");
        }
        String content = renderContent(task);
        try {
            JsonNode response = client.printLabelMessage(sn, content, Math.max(1, task.copies()), imageBase64);
            return toPrintResult(task, response);
        } catch (FeieApiException ex) {
            log.error("Feie label print failed: orderId={}, sn={}", task.orderId(), sn, ex);
            return new PrintResult(false, task.orderId(), "Feie API error: " + ex.getMessage());
        }
    }

    /**
     * 飞鹅特有:批量添加打印机(返回飞鹅原始响应,交由调用方解析)。
     */
    public JsonNode addPrintersRaw(String printerContent) {
        return client.printerAddList(printerContent);
    }

    /**
     * 飞鹅特有:批量删除打印机。
     */
    public JsonNode deletePrintersRaw(String snList) {
        return client.printerDelList(snList);
    }

    /**
     * 飞鹅特有:修改打印机信息(备注名 / 流量卡号码)。
     */
    public JsonNode editPrinterRaw(String sn, String name, String phoneNum) {
        return client.printerEdit(sn, name, phoneNum);
    }

    private String renderContent(PrintTask task) {
        String contentType = task.contentType();
        if (CONTENT_TYPE_PRINT_DOCUMENT.equalsIgnoreCase(contentType) || contentType == null) {
            return renderer.render(task.content());
        }
        // 已是纯文本 / ESC/POS 文本:直接透传
        return task.content();
    }

    private String required(PrinterConfig config, String key) {
        if (config == null || config.properties() == null) {
            return null;
        }
        String value = config.properties().get(key);
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
