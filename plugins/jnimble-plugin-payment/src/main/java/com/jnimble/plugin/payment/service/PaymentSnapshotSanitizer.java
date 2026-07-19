package com.jnimble.plugin.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentSnapshotSanitizer {

    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "apikey", "api_key", "password", "passwd", "secret",
            "token", "accesstoken", "access_token", "refreshtoken", "refresh_token",
            "cardnumber", "card_number", "cvv", "cvc", "pin", "privatekey", "private_key"
    );

    private final ObjectMapper objectMapper;

    public PaymentSnapshotSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode sanitize(Object value) {
        JsonNode root = value instanceof JsonNode node ? node.deepCopy() : objectMapper.valueToTree(value);
        return sanitizeNode(root, null);
    }

    private JsonNode sanitizeNode(JsonNode node, String fieldName) {
        if (fieldName != null && isSensitive(fieldName)) {
            return objectMapper.getNodeFactory().textNode(MASK);
        }
        if (node == null || node.isNull()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.properties().forEach(entry -> object.set(
                    entry.getKey(), sanitizeNode(entry.getValue(), entry.getKey())
            ));
            return object;
        }
        if (node.isArray()) {
            ArrayNode source = (ArrayNode) node;
            ArrayNode sanitized = objectMapper.createArrayNode();
            source.forEach(item -> sanitized.add(sanitizeNode(item, fieldName)));
            return sanitized;
        }
        return node;
    }

    private boolean isSensitive(String fieldName) {
        String normalized = fieldName.replace("-", "_").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.endsWith("_secret")
                || normalized.endsWith("_token")
                || normalized.endsWith("_password")
                || normalized.endsWith("_key");
    }
}
