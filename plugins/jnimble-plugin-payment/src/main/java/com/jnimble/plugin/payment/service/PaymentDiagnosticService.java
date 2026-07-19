package com.jnimble.plugin.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.payment.mapper.PaymentEventMapper;
import com.jnimble.plugin.payment.model.entity.PaymentEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentDiagnosticService {

    private final PaymentEventMapper paymentEventMapper;
    private final PaymentSnapshotSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public PaymentDiagnosticService(
            PaymentEventMapper paymentEventMapper,
            PaymentSnapshotSanitizer sanitizer,
            ObjectMapper objectMapper
    ) {
        this.paymentEventMapper = paymentEventMapper;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
    }

    public PaymentEventEntity record(
            String paymentId,
            String orderId,
            String eventType,
            String status,
            Object request,
            Object response,
            String errorMessage
    ) {
        PaymentEventEntity event = new PaymentEventEntity();
        event.setId(UUID.randomUUID().toString().replace("-", ""));
        event.setPaymentId(paymentId);
        event.setOrderId(orderId);
        event.setEventType(eventType);
        event.setStatus(status);
        event.setRequestSnapshot(snapshot(request));
        event.setResponseSnapshot(snapshot(response));
        event.setErrorMessage(errorMessage);
        event.setCreatedAt(Instant.now());
        return MapperUtils.insert(paymentEventMapper, event);
    }

    public List<PaymentEventEntity> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return MapperUtils.selectList(paymentEventMapper, PaymentEventEntity.class,
                wrapper -> wrapper.orderByDesc("created_at").last("LIMIT " + safeLimit));
    }

    public PaymentEventEntity findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return MapperUtils.getById(paymentEventMapper, id.trim(), null);
    }

    public String snapshot(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode sanitized = sanitizer.sanitize(value);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sanitized);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize payment diagnostic snapshot", ex);
        }
    }
}
