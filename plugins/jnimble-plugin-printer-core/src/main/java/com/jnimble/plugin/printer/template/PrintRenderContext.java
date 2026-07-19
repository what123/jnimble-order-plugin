package com.jnimble.plugin.printer.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrintRenderContext {

    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    private final JsonNode data;
    private final ObjectMapper objectMapper;

    public PrintRenderContext(JsonNode data, ObjectMapper objectMapper) {
        this.data = data;
        this.objectMapper = objectMapper;
    }

    public JsonNode data() {
        return data;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public JsonNode resolve(String path) {
        if (path == null || path.isBlank()) {
            return MissingNode.getInstance();
        }
        JsonNode current = data;
        for (String segment : path.split("\\.")) {
            if (current == null || !current.isObject()) {
                return MissingNode.getInstance();
            }
            current = current.path(segment);
        }
        return current == null ? MissingNode.getInstance() : current;
    }

    public String text(String path) {
        JsonNode value = resolve(path);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }

    public String interpolate(String template) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = INTERPOLATION.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(text(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String formatValue(JsonNode value, String format) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        if ("MONEY".equalsIgnoreCase(format) && value.isNumber()) {
            return value.decimalValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return value.asText();
    }
}
