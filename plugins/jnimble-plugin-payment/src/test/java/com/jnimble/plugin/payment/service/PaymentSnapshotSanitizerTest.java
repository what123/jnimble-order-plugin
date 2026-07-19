package com.jnimble.plugin.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentSnapshotSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentSnapshotSanitizer sanitizer = new PaymentSnapshotSanitizer(objectMapper);

    @Test
    void sanitizesSensitiveValuesRecursively() {
        Map<String, Object> snapshot = Map.of(
                "authorization", "Bearer secret",
                "metadata", Map.of(
                        "customer", "guest",
                        "credentials", List.of(
                                Map.of("apiKey", "key-1"),
                                Map.of("password", "pass-1")
                        )
                ),
                "items", List.of(Map.of("card_number", "6222000000000000", "amount", 10))
        );

        JsonNode sanitized = sanitizer.sanitize(snapshot);

        assertEquals("***", sanitized.path("authorization").asText());
        assertEquals("guest", sanitized.path("metadata").path("customer").asText());
        assertEquals("***", sanitized.path("metadata").path("credentials").get(0).path("apiKey").asText());
        assertEquals("***", sanitized.path("metadata").path("credentials").get(1).path("password").asText());
        assertEquals("***", sanitized.path("items").get(0).path("card_number").asText());
        assertEquals(10, sanitized.path("items").get(0).path("amount").asInt());
    }
}
