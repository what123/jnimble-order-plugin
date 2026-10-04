package com.jnimble.plugin.printer.feie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.printer.feie.FeieApiClient.FeieApiException;
import com.jnimble.plugin.printer.spi.PrintResult;
import com.jnimble.plugin.printer.spi.PrintTask;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterOperationResult;
import com.jnimble.plugin.printer.spi.PrinterOrderStatistics;
import com.jnimble.plugin.printer.spi.PrinterStatus;
import com.jnimble.plugin.printer.template.FeieEscPosRenderer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeiePrinterDriverTest {

    private FeieApiClient client;
    private FeieEscPosRenderer renderer;
    private FeiePrinterDriver driver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = mock(FeieApiClient.class);
        renderer = new FeieEscPosRenderer(objectMapper);
        driver = new FeiePrinterDriver(client, renderer);
    }

    @Test
    @DisplayName("driverId 应返回 feie")
    void driverIdShouldReturnFeie() {
        assertEquals("feie", driver.driverId());
    }

    @Test
    @DisplayName("driverName 应返回飞鹅云打印")
    void driverNameShouldReturnFeieCloudPrint() {
        assertEquals("飞鹅云打印", driver.driverName());
    }

    @Test
    @DisplayName("含 user/ukey/sn 配置时 supports 返回 true")
    void supportsShouldReturnTrueWhenConfigComplete() {
        PrinterConfig config = new PrinterConfig("feie", Map.of("user", "u1", "ukey", "k1", "sn", "s1"));
        assertTrue(driver.supports(config));
    }

    @Test
    @DisplayName("缺 sn 时 supports 返回 false")
    void supportsShouldReturnFalseWhenSnMissing() {
        PrinterConfig config = new PrinterConfig("feie", Map.of("user", "u1", "ukey", "k1"));
        assertFalse(driver.supports(config));
    }

    @Test
    @DisplayName("非 feie driverId 时 supports 返回 false")
    void supportsShouldReturnFalseForOtherDriver() {
        PrinterConfig config = new PrinterConfig("other", Map.of("user", "u1", "ukey", "k1", "sn", "s1"));
        assertFalse(driver.supports(config));
    }

    @Test
    @DisplayName("飞鹅返回 ret=0 时应标记成功,并回写 feieOrderId")
    void printShouldReturnSuccessWhenRetIsZero() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        response.put("msg", "ok");
        response.put("data", "816501678_20160919184316_1419533539");
        when(client.printMessage(eq("s1"), anyString(), anyInt())).thenReturn(response);

        PrintTask task = new PrintTask(
                "order-123", documentJson(), "application/vnd.jnimble.print-document+json; charset=UTF-8",
                "PLAIN", "KITCHEN", 2);
        PrintResult result = driver.print(task, config());

        assertTrue(result.success());
        assertEquals("816501678_20160919184316_1419533539", result.jobId());
    }

    @Test
    @DisplayName("飞鹅返回非 0 ret 时应标记失败,消息含错误码")
    void printShouldReturnFailureWhenRetNonZero() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", -2);
        response.put("msg", "参数错误");
        response.put("data", (String) null);
        when(client.printMessage(eq("s1"), anyString(), anyInt())).thenReturn(response);

        PrintTask task = new PrintTask("order-123", documentJson(),
                "application/vnd.jnimble.print-document+json; charset=UTF-8", "PLAIN", "KITCHEN", 1);
        PrintResult result = driver.print(task, config());

        assertFalse(result.success());
        assertTrue(result.message().contains("-2"));
        assertTrue(result.message().contains("参数错误"));
    }

    @Test
    @DisplayName("飞鹅 API 抛异常时应返回失败,不向上传播")
    void printShouldReturnFailureWhenApiClientThrows() {
        when(client.printMessage(anyString(), anyString(), anyInt()))
                .thenThrow(new FeieApiException("connection refused", new RuntimeException()));

        PrintTask task = new PrintTask("order-123", documentJson(),
                "application/vnd.jnimble.print-document+json; charset=UTF-8", "PLAIN", "KITCHEN", 1);
        PrintResult result = driver.print(task, config());

        assertFalse(result.success());
        assertTrue(result.message().contains("connection refused"));
    }

    @Test
    @DisplayName("缺少 sn 时应直接返回失败,不调 API")
    void printShouldReturnFailureWhenSnMissing() {
        PrintTask task = new PrintTask("order-123", documentJson(), "text/plain", "PLAIN", "KITCHEN", 1);
        PrinterConfig config = new PrinterConfig("feie", Map.of("user", "u1", "ukey", "k1"));

        PrintResult result = driver.print(task, config);

        assertFalse(result.success());
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("查询状态:data 含\"离线\"时返回 OFFLINE")
    void queryStatusShouldReturnOfflineWhenDataSaysOffline() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        response.put("data", "离线");
        when(client.queryPrinterStatus("s1")).thenReturn(response);

        assertEquals(PrinterStatus.OFFLINE, driver.queryStatus(config()));
    }

    @Test
    @DisplayName("查询状态:data 含\"在线\"时返回 ONLINE")
    void queryStatusShouldReturnOnlineWhenDataSaysOnline() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        response.put("data", "在线,正常工作");
        when(client.queryPrinterStatus("s1")).thenReturn(response);

        assertEquals(PrinterStatus.ONLINE, driver.queryStatus(config()));
    }

    @Test
    @DisplayName("查询状态:ret 非 0 时返回 ERROR")
    void queryStatusShouldReturnErrorWhenRetNonZero() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", -3);
        response.put("data", "");
        when(client.queryPrinterStatus("s1")).thenReturn(response);

        assertEquals(PrinterStatus.ERROR, driver.queryStatus(config()));
    }

    @Test
    @DisplayName("查询状态:API 异常时返回 ERROR,不向上传播")
    void queryStatusShouldReturnErrorWhenApiThrows() {
        when(client.queryPrinterStatus("s1")).thenThrow(new FeieApiException("timeout", new RuntimeException()));
        assertEquals(PrinterStatus.ERROR, driver.queryStatus(config()));
    }

    @Test
    @DisplayName("纯文本 contentType 应透传,不走 renderer")
    void printShouldPassThroughPlainTextContent() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        response.put("msg", "ok");
        response.put("data", "ext-id");
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        when(client.printMessage(eq("s1"), contentCaptor.capture(), eq(1))).thenReturn(response);

        PrintTask task = new PrintTask("order-123", "<CB>raw text</CB>", "text/plain; charset=UTF-8", "PLAIN", "KITCHEN", 1);
        PrintResult result = driver.print(task, config());

        assertTrue(result.success());
        assertEquals("<CB>raw text</CB>", contentCaptor.getValue());
    }

    @Test
    @DisplayName("clearPendingQueue:ret=0 时应返回 success")
    void clearQueueShouldReturnSuccessWhenRetIsZero() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        response.put("msg", "ok");
        response.put("data", true);
        when(client.delPrinterSQS("s1")).thenReturn(response);

        PrinterOperationResult result = driver.clearPendingQueue(config());

        assertTrue(result.success());
        assertEquals("feie", result.driverId());
    }

    @Test
    @DisplayName("clearPendingQueue:ret 非 0 时应返回 failure")
    void clearQueueShouldReturnFailureWhenRetNonZero() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", -1);
        response.put("msg", "printer offline");
        when(client.delPrinterSQS("s1")).thenReturn(response);

        PrinterOperationResult result = driver.clearPendingQueue(config());

        assertFalse(result.success());
        assertTrue(result.message().contains("printer offline"));
    }

    @Test
    @DisplayName("clearPendingQueue:API 异常时应返回 failure")
    void clearQueueShouldReturnFailureWhenApiThrows() {
        when(client.delPrinterSQS("s1"))
                .thenThrow(new FeieApiException("timeout", new RuntimeException()));

        PrinterOperationResult result = driver.clearPendingQueue(config());

        assertFalse(result.success());
        assertTrue(result.message().contains("timeout"));
    }

    @Test
    @DisplayName("queryOrderStatistics:ret=0 时应返回 printed/waiting")
    void queryOrderStatisticsShouldReturnCountsWhenRetIsZero() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        response.put("msg", "ok");
        ObjectNode data = response.putObject("data");
        data.put("printed", 15);
        data.put("waiting", 3);
        when(client.queryOrderInfoByDate("s1", "2026-07-19")).thenReturn(response);

        PrinterOrderStatistics stats = driver.queryOrderStatistics(config(), "2026-07-19");

        assertTrue(stats.supported());
        assertEquals(15, stats.printed());
        assertEquals(3, stats.waiting());
    }

    @Test
    @DisplayName("queryOrderStatistics:ret 非 0 时应返回 unsupported")
    void queryOrderStatisticsShouldReturnUnsupportedWhenRetNonZero() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", -1);
        when(client.queryOrderInfoByDate(eq("s1"), anyString())).thenReturn(response);

        PrinterOrderStatistics stats = driver.queryOrderStatistics(config(), "2026-07-19");

        assertFalse(stats.supported());
    }

    @Test
    @DisplayName("queryOrderStatistics:API 异常时应返回 unsupported")
    void queryOrderStatisticsShouldReturnUnsupportedWhenApiThrows() {
        when(client.queryOrderInfoByDate(eq("s1"), anyString()))
                .thenThrow(new FeieApiException("connection refused", new RuntimeException()));

        PrinterOrderStatistics stats = driver.queryOrderStatistics(config(), "2026-07-19");

        assertFalse(stats.supported());
    }

    @Test
    @DisplayName("printLabel:ret=0 时应返回成功")
    void printLabelShouldReturnSuccessWhenRetIsZero() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        response.put("msg", "ok");
        response.put("data", "label-id");
        when(client.printLabelMessage(eq("s1"), anyString(), eq(1), any())).thenReturn(response);

        PrintTask task = new PrintTask("order-123", "<CB>label content</CB>",
                "text/plain; charset=UTF-8", "PLAIN", "LABEL", 1);
        PrintResult result = driver.printLabel(task, config(), null);

        assertTrue(result.success());
        assertEquals("label-id", result.jobId());
    }

    @Test
    @DisplayName("printLabel:缺 sn 时应返回失败")
    void printLabelShouldReturnFailureWhenSnMissing() {
        PrintTask task = new PrintTask("order-123", "content",
                "text/plain; charset=UTF-8", "PLAIN", "LABEL", 1);
        PrinterConfig config = new PrinterConfig("feie", Map.of("user", "u1", "ukey", "k1"));

        PrintResult result = driver.printLabel(task, config, null);

        assertFalse(result.success());
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("addPrintersRaw 应直接透传到 client")
    void addPrintersRawShouldDelegateToClient() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        when(client.printerAddList("sn1#key1#remark1#")).thenReturn(response);

        JsonNode result = driver.addPrintersRaw("sn1#key1#remark1#");

        assertEquals(0, result.path("ret").asInt());
    }

    @Test
    @DisplayName("deletePrintersRaw 应直接透传到 client")
    void deletePrintersRawShouldDelegateToClient() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        when(client.printerDelList("sn1-sn2")).thenReturn(response);

        JsonNode result = driver.deletePrintersRaw("sn1-sn2");

        assertEquals(0, result.path("ret").asInt());
    }

    @Test
    @DisplayName("editPrinterRaw 应直接透传到 client")
    void editPrinterRawShouldDelegateToClient() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("ret", 0);
        when(client.printerEdit("s1", "前台1号", "13800001111")).thenReturn(response);

        JsonNode result = driver.editPrinterRaw("s1", "前台1号", "13800001111");

        assertEquals(0, result.path("ret").asInt());
    }

    private PrinterConfig config() {
        return new PrinterConfig("feie", Map.of("user", "u1", "ukey", "k1", "sn", "s1"));
    }

    private String documentJson() {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schema", "jnimble.print-document.v1");
        document.put("schemaVersion", 1);
        ObjectNode paper = document.putObject("paper");
        paper.put("widthMm", 58);
        ObjectNode row = document.putArray("rows").addObject();
        row.put("id", "row-1");
        ObjectNode cell = row.putArray("cells").addObject();
        cell.put("blockId", "b1");
        cell.put("blockType", "title");
        ObjectNode element = cell.putArray("elements").addObject();
        element.put("kind", "TEXT");
        element.put("text", "测试门店");
        element.put("align", "CENTER");
        element.put("size", "LARGE");
        element.put("bold", true);
        return document.toString();
    }
}
