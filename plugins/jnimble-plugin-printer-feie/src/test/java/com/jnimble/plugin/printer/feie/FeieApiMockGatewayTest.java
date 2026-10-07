package com.jnimble.plugin.printer.feie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnimble.plugin.printer.spi.PrintResult;
import com.jnimble.plugin.printer.spi.PrintTask;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterStatus;
import com.jnimble.plugin.printer.template.FeieEscPosRenderer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用本地 mock 网关模拟飞鹅云 API,端到端验证 driver → client → 网关 的协议:
 * 请求公共参数(user/stime/sig/apiname)、签名算法、各接口动作名与业务参数,以及响应解析。
 */
class FeieApiMockGatewayTest {

    private static final String USER = "jnimble_user";
    private static final String UKEY = "jnimble_ukey";

    private HttpServer server;
    private volatile String lastPath;
    private volatile Map<String, String> lastParams = Map.of();
    private volatile String responseBody = "{\"ret\":0,\"msg\":\"ok\"}";

    private FeieApiClient client;
    private FeiePrinterDriver driver;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/Api/Open/", this::handle);
        server.start();
        int port = server.getAddress().getPort();
        client = new FeieApiClient(USER, UKEY, "http://127.0.0.1:" + port + "/Api/Open/");
        driver = new FeiePrinterDriver(client, new FeieEscPosRenderer(new ObjectMapper()));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("打印:使用官方公共参数 + 正确签名(sig),动作 Open_printMsg")
    void printUsesOfficialProtocol() {
        responseBody = "{\"ret\":0,\"msg\":\"ok\",\"data\":\"FEIE-ORDER-1\"}";
        PrinterConfig config = new PrinterConfig("feie", Map.of("sn", "SN-001"));
        PrintTask task = new PrintTask("ORD-1", "<CB>hello</CB>", "text/plain", null, "RECEIPT", 2);

        PrintResult result = driver.print(task, config);

        assertTrue(result.success(), "print should succeed");
        assertEquals("FEIE-ORDER-1", result.jobId());
        assertEquals("/Api/Open/", lastPath);
        assertEquals("Open_printMsg", lastParams.get("apiname"));
        assertEquals("SN-001", lastParams.get("sn"));
        assertEquals("<CB>hello</CB>", lastParams.get("content"));
        assertEquals("2", lastParams.get("times"));
        assertCommonParams();
        assertFalse(lastParams.containsKey("sign"), "must use 'sig', not 'sign'");
    }

    @Test
    @DisplayName("查询状态:动作 Open_queryPrinterStatus,解析在线")
    void queryStatusMapsOnline() {
        responseBody = "{\"ret\":0,\"data\":\"在线,工作状态正常\"}";
        PrinterStatus status = driver.queryStatus(new PrinterConfig("feie", Map.of("sn", "SN-001")));

        assertEquals(PrinterStatus.ONLINE, status);
        assertEquals("Open_queryPrinterStatus", lastParams.get("apiname"));
        assertEquals("SN-001", lastParams.get("sn"));
        assertCommonParams();
    }

    @Test
    @DisplayName("查询订单统计:动作 Open_queryOrderInfoByDate + date 参数")
    void queryOrderStatisticsSendsDate() {
        responseBody = "{\"ret\":0,\"msg\":\"ok\",\"data\":{\"printed\":10,\"waiting\":2}}";
        driver.queryOrderStatistics(new PrinterConfig("feie", Map.of("sn", "SN-001")), "2026-10-05");

        assertEquals("Open_queryOrderInfoByDate", lastParams.get("apiname"));
        assertEquals("2026-10-05", lastParams.get("date"));
        assertEquals("SN-001", lastParams.get("sn"));
        assertCommonParams();
    }

    @Test
    @DisplayName("清空待打印队列:动作 Open_delPrinterSqs")
    void clearQueueUsesDelPrinterSqs() {
        responseBody = "{\"ret\":0,\"msg\":\"ok\",\"data\":true}";
        driver.clearPendingQueue(new PrinterConfig("feie", Map.of("sn", "SN-001")));

        assertEquals("Open_delPrinterSQS".toLowerCase(), lastParams.get("apiname").toLowerCase());
        assertCommonParams();
    }

    @Test
    @DisplayName("飞鹅拒绝(ret!=0)时返回失败")
    void printRejectedReturnsFailure() {
        responseBody = "{\"ret\":1,\"msg\":\"SN不存在\"}";
        PrintResult result = driver.print(
                new PrintTask("ORD-1", "x", "text/plain", null, "RECEIPT", 1),
                new PrinterConfig("feie", Map.of("sn", "SN-001")));

        assertFalse(result.success());
        assertTrue(result.message().contains("Feie rejected"));
    }

    private void assertCommonParams() {
        assertEquals(USER, lastParams.get("user"));
        String stime = lastParams.get("stime");
        assertNotNull(stime);
        assertTrue(stime.matches("\\d{10}"), "stime must be a 10-digit unix seconds: " + stime);
        String sig = lastParams.get("sig");
        assertNotNull(sig, "sig is required");
        assertTrue(sig.matches("[0-9a-f]{40}"), "sig must be 40-char lowercase hex: " + sig);
        assertEquals(sha1(USER + UKEY + stime), sig, "sig must equal SHA1(user+ukey+stime)");
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastPath = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastParams = parseForm(body);
        byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, out.length);
        exchange.getResponseBody().write(out);
        exchange.close();
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> params = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    private static String sha1(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
