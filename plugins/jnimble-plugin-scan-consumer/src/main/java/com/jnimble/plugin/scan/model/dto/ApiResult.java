package com.jnimble.plugin.scan.model.dto;

import java.util.HashMap;
import java.util.Map;

public class ApiResult {

    private ApiResult() {}

    public static Map<String, Object> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", "success");
        result.put("data", data);
        return result;
    }

    public static Map<String, Object> success() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", "success");
        result.put("data", new HashMap<>());
        return result;
    }

    public static Map<String, Object> success(String msg, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", msg);
        result.put("data", data);
        return result;
    }

    public static Map<String, Object> error(int code, String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("msg", msg);
        result.put("data", null);
        return result;
    }

    public static Map<String, Object> error(String msg) {
        return error(1, msg);
    }
}
