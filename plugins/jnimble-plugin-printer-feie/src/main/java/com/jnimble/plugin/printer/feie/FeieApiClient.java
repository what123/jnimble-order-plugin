package com.jnimble.plugin.printer.feie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 飞鹅云打印 API 客户端。
 *
 * <p>统一网关 {@code https://api.feieyun.cn/Api/Open/},所有接口 POST multipart/form-data。
 * 签名 {@code sig = SHA1(user + ukey + sysTime)}(小写十六进制)。</p>
 *
 * <p>本类只负责通信,不解析业务语义。返回值为飞鹅原始 JSON。</p>
 */
public class FeieApiClient {

    private static final Logger log = LoggerFactory.getLogger(FeieApiClient.class);

    private static final String DEFAULT_GATEWAY = "https://api.feieyun.cn/Api/Open/";
    private static final String FIELD_USER = "user";
    private static final String FIELD_UKEY = "ukey";
    private static final String FIELD_STIME = "stime";
    private static final String FIELD_SIGN = "sig";
    private static final String FIELD_APINAME = "apiname";
    private static final String FIELD_SN = "sn";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_TIMES = "times";
    private static final String FIELD_EXPIRED = "expired";
    private static final String FIELD_BACK_URL = "backurl";
    private static final String FIELD_ORDER_ID = "orderid";
    private static final String FIELD_DATE = "date";
    private static final String FIELD_PRINTER_CONTENT = "printerContent";
    private static final String FIELD_SN_LIST = "snlist";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_PHONE_NUM = "phonenum";
    private static final String FIELD_IMG = "img";

    private static final String API_PRINT_MSG = "Open_printMsg";
    private static final String API_PRINT_LABEL_MSG = "Open_printLabelMsg";
    private static final String API_QUERY_PRINTER_STATUS = "Open_queryPrinterStatus";
    private static final String API_QUERY_ORDER_STATE = "Open_queryOrderState";
    private static final String API_QUERY_ORDER_INFO_BY_DATE = "Open_queryOrderInfoByDate";
    private static final String API_PRINTER_ADD_LIST = "Open_printerAddList";
    private static final String API_PRINTER_DEL_LIST = "Open_printerDelList";
    private static final String API_PRINTER_EDIT = "Open_printerEdit";
    private static final String API_DEL_PRINTER_SQS = "Open_delPrinterSQS";

    private final String user;
    private final String ukey;
    private final String gateway;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FeieApiClient(String user, String ukey) {
        this(user, ukey, DEFAULT_GATEWAY, defaultHttpClient(), new ObjectMapper());
    }

    public FeieApiClient(String user, String ukey, String gateway) {
        this(user, ukey, gateway, defaultHttpClient(), new ObjectMapper());
    }

    public FeieApiClient(String user, String ukey, String gateway, HttpClient httpClient, ObjectMapper objectMapper) {
        this.user = user;
        this.ukey = ukey;
        this.gateway = gateway;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 发送打印订单到飞鹅云小票机。
     *
     * @param sn      打印机编号
     * @param content ESC/POS 文本(含飞鹅标签)
     * @param copies  打印份数,1 ~ 9
     * @return 飞鹅响应 JSON(含 {@code msg}/{@code ret}/{@code data}/{@code serverExecutedTime})
     */
    public JsonNode printMessage(String sn, String content, int copies) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FIELD_APINAME, API_PRINT_MSG);
        form.put(FIELD_SN, sn);
        form.put(FIELD_CONTENT, content);
        if (copies > 1) {
            form.put(FIELD_TIMES, String.valueOf(Math.min(copies, 9)));
        }
        return post(form);
    }

    /**
     * 查询打印机在线/离线状态。
     *
     * @param sn 打印机编号
     * @return 飞鹅响应 JSON,{@code data} 为状态描述("在线,正常工作" 等)
     */
    public JsonNode queryPrinterStatus(String sn) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FIELD_APINAME, API_QUERY_PRINTER_STATUS);
        form.put(FIELD_SN, sn);
        return post(form);
    }

    /**
     * 查询订单是否打印成功。
     *
     * @param orderId 飞鹅 {@code Open_printMsg} 返回的订单 ID
     * @return 飞鹅响应 JSON,{@code data} 为 "1"(已打印) / "0"(未打印)
     */
    public JsonNode queryOrderState(String orderId) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FIELD_APINAME, API_QUERY_ORDER_STATE);
        form.put(FIELD_ORDER_ID, orderId);
        return post(form);
    }

    /**
     * 标签机打印订单(标签机专用,不能用于小票机)。
     *
     * @param sn      打印机编号
     * @param content ESC/POS 文本(可含 {@code <QR>}、{@code <BR>} 等标签)
     * @param copies  打印份数,1 ~ 9
     * @param img     可选 base64 图片(标签机专用)
     * @return 飞鹅响应 JSON
     */
    public JsonNode printLabelMessage(String sn, String content, int copies, String img) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FIELD_APINAME, API_PRINT_LABEL_MSG);
        form.put(FIELD_SN, sn);
        form.put(FIELD_CONTENT, content);
        if (copies > 1) {
            form.put(FIELD_TIMES, String.valueOf(Math.min(copies, 9)));
        }
        if (img != null && !img.isBlank()) {
            form.put(FIELD_IMG, img);
        }
        return post(form);
    }

    /**
     * 批量添加打印机。
     *
     * <p>格式:{@code sn1#key1#remark1#carnum1\nsn2#key2#remark2#carnum2},每次最多 100 行。</p>
     *
     * @param printerContent 打印机批量字符串
     * @return 飞鹅响应 JSON,{@code data.ok} / {@code data.no} 分别为成功和失败列表
     */
    public JsonNode printerAddList(String printerContent) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FIELD_APINAME, API_PRINTER_ADD_LIST);
        form.put(FIELD_PRINTER_CONTENT, printerContent);
        return post(form);
    }

    /**
     * 批量删除打印机。
     *
     * @param snList 打印机编号,多台用 {@code -} 连接({@code sn1-sn2-sn3})
     * @return 飞鹅响应 JSON
     */
    public JsonNode printerDelList(String snList) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FIELD_APINAME, API_PRINTER_DEL_LIST);
        form.put(FIELD_SN_LIST, snList);
        return post(form);
    }

    /**
     * 修改打印机信息(备注名 / 流量卡号码)。
     *
     * @param sn       打印机编号
     * @param name     备注名
     * @param phoneNum 流量卡号码(可空)
     * @return 飞鹅响应 JSON
     */
    public JsonNode printerEdit(String sn, String name, String phoneNum) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FIELD_APINAME, API_PRINTER_EDIT);
        form.put(FIELD_SN, sn);
        form.put(FIELD_NAME, name);
        if (phoneNum != null && !phoneNum.isBlank()) {
            form.put(FIELD_PHONE_NUM, phoneNum);
        }
        return post(form);
    }

    /**
     * 清空指定打印机的待打印队列。
     *
     * @param sn 打印机编号
     * @return 飞鹅响应 JSON,{@code data} 为 true/false
     */
    public JsonNode delPrinterSQS(String sn) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FIELD_APINAME, API_DEL_PRINTER_SQS);
        form.put(FIELD_SN, sn);
        return post(form);
    }

    /**
     * 查询指定打印机某天的订单统计。
     *
     * @param sn   打印机编号
     * @param date 日期格式 {@code yyyy-MM-dd}
     * @return 飞鹅响应 JSON,{@code data} 含 {@code printed}/{@code waiting} 等统计
     */
    public JsonNode queryOrderInfoByDate(String sn, String date) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FIELD_APINAME, API_QUERY_ORDER_INFO_BY_DATE);
        form.put(FIELD_SN, sn);
        form.put(FIELD_DATE, date);
        return post(form);
    }

    private JsonNode post(Map<String, String> form) {
        Map<String, String> signed = sign(form);
        String body = encodeForm(signed);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gateway))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body();
            if (log.isDebugEnabled()) {
                log.debug("Feie API {} response: status={}, body={}",
                        signed.get(FIELD_APINAME), response.statusCode(), responseBody);
            }
            return objectMapper.readTree(responseBody);
        } catch (Exception ex) {
            throw new FeieApiException("Failed to call Feie API " + signed.get(FIELD_APINAME), ex);
        }
    }

    private Map<String, String> sign(Map<String, String> form) {
        String stime = String.valueOf(System.currentTimeMillis() / 1000);
        String sign = sha1(user + ukey + stime);
        form.put(FIELD_USER, user);
        form.put(FIELD_STIME, stime);
        form.put(FIELD_SIGN, sign);
        return form;
    }

    private static String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-1 not available", ex);
        }
    }

    private static String encodeForm(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static class FeieApiException extends RuntimeException {
        public FeieApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
