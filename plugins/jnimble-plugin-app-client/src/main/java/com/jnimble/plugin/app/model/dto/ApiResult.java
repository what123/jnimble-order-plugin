package com.jnimble.plugin.app.model.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiResult {

    private ApiResult() {
    }

    public static Map<String, Object> success(Object data) {
        return message(0, "success", data);
    }

    public static Map<String, Object> success(String msg, Object data) {
        return message(0, msg, data);
    }

    public static Map<String, Object> error(String msg) {
        return message(1, msg, null);
    }

    public static Map<String, Object> error(int code, String msg) {
        return message(code, msg, null);
    }

    public static Map<String, Object> message(int code, String msg, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("msg", msg == null ? "" : msg);
        result.put("data", data);
        return result;
    }
}
