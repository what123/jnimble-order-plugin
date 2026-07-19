package com.jnimble.plugin.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.payment.mapper.PaymentEventMapper;
import com.jnimble.plugin.payment.model.entity.PaymentEventEntity;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentDiagnosticServiceTest {

    private final PaymentEventMapper eventMapper = mock(PaymentEventMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentDiagnosticService service = new PaymentDiagnosticService(
            eventMapper, new PaymentSnapshotSanitizer(objectMapper), objectMapper
    );
    private MockedStatic<MapperUtils> mapperUtils;

    @BeforeEach
    void setUp() {
        mapperUtils = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtils.close();
    }

    @Test
    void recordsSanitizedRequestAndResponseSnapshots() {
        mapperUtils.when(() -> MapperUtils.insert(eq(eventMapper), any(PaymentEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        PaymentEventEntity event = service.record(
                "pay-1", "order-1", "PAYMENT", "SUCCEEDED",
                Map.of("token", "secret", "nested", Map.of("amount", 10)),
                Map.of("status", "PAID", "privateKey", "secret"), null
        );

        assertEquals("pay-1", event.getPaymentId());
        assertTrue(event.getRequestSnapshot().contains("\"token\" : \"***\""));
        assertTrue(event.getRequestSnapshot().contains("\"amount\" : 10"));
        assertTrue(event.getResponseSnapshot().contains("\"privateKey\" : \"***\""));
        assertFalse(event.getRequestSnapshot().contains("secret"));
        assertNotNull(event.getCreatedAt());
    }
}
