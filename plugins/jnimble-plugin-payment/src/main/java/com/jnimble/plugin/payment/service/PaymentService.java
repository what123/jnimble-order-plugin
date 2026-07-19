package com.jnimble.plugin.payment.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.payment.mapper.PaymentMapper;
import com.jnimble.plugin.payment.model.entity.PaymentEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentDiagnosticService diagnosticService;

    public PaymentService(PaymentMapper paymentMapper, PaymentDiagnosticService diagnosticService) {
        this.paymentMapper = paymentMapper;
        this.diagnosticService = diagnosticService;
    }

    public PaymentEntity processPayment(String orderId, String method, BigDecimal amount,
                                        BigDecimal received, String operator) {
        return processPayment(orderId, method, amount, received, operator,
                UUID.randomUUID().toString().replace("-", ""));
    }

    public PaymentEntity processPayment(String orderId, String method, BigDecimal amount,
                                        BigDecimal received, String operator, String idempotencyKey) {
        return processPayment(orderId, method, amount, received, operator, idempotencyKey, null);
    }

    @Transactional
    public PaymentEntity processPayment(String orderId, String method, BigDecimal amount,
                                        BigDecimal received, String operator, String idempotencyKey,
                                        Object requestSnapshot) {
        try {
            PaymentEntity payment = createPayment(
                    orderId, method, amount, received, operator, idempotencyKey, requestSnapshot
            );
            if (requestSnapshot != null) {
                diagnosticService.record(
                        payment.getId(), payment.getOrderId(), "PAYMENT", "SUCCEEDED",
                        requestSnapshot, responseSnapshot(payment), null
                );
            }
            return payment;
        } catch (RuntimeException ex) {
            if (requestSnapshot != null) {
                diagnosticService.record(
                        null, orderId, "PAYMENT", "FAILED", requestSnapshot, null, ex.getMessage()
                );
            }
            throw ex;
        }
    }

    private PaymentEntity createPayment(String orderId, String method, BigDecimal amount,
                                        BigDecimal received, String operator, String idempotencyKey,
                                        Object requestSnapshot) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Payment idempotency key is required");
        }
        PaymentEntity existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Payment amount must not be negative");
        }

        String normalizedMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        BigDecimal normalizedReceived = received;
        if (!"CASH".equals(normalizedMethod) && normalizedReceived == null) {
            normalizedReceived = amount;
        }
        if ("CASH".equals(normalizedMethod) && normalizedReceived == null && amount.signum() == 0) {
            normalizedReceived = BigDecimal.ZERO;
        }
        if (normalizedReceived == null || normalizedReceived.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Received amount must cover the payment amount");
        }

        PaymentEntity entity = new PaymentEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setOrderId(orderId);
        entity.setMethod(normalizedMethod);
        entity.setProvider("LOCAL");
        entity.setProviderTradeNo(entity.getId());
        entity.setIdempotencyKey(idempotencyKey.trim());
        entity.setAmount(amount);
        entity.setReceivedAmount(normalizedReceived);
        entity.setChangeAmount(normalizedReceived.subtract(amount).max(BigDecimal.ZERO));
        entity.setStatus("PAID");
        entity.setOperator(operator);
        entity.setPaidAt(Instant.now());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        if (requestSnapshot != null) {
            entity.setRequestSnapshot(diagnosticService.snapshot(requestSnapshot));
            entity.setResponseSnapshot(diagnosticService.snapshot(responseSnapshot(entity)));
        }

        MapperUtils.insert(paymentMapper, entity);
        return entity;
    }

    private Map<String, Object> responseSnapshot(PaymentEntity payment) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", payment.getStatus());
        response.put("paymentId", payment.getId());
        response.put("providerTradeNo", payment.getProviderTradeNo());
        response.put("amount", payment.getAmount());
        response.put("changeAmount", payment.getChangeAmount());
        return response;
    }

    public PaymentEntity findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        List<PaymentEntity> payments = MapperUtils.selectList(paymentMapper, PaymentEntity.class,
                wrapper -> wrapper.eq("idempotency_key", idempotencyKey.trim()).last("LIMIT 1"));
        return payments.isEmpty() ? null : payments.getFirst();
    }

    public List<PaymentEntity> listByOrderId(String orderId) {
        return MapperUtils.selectList(paymentMapper, PaymentEntity.class,
                wrapper -> wrapper.eq("order_id", orderId));
    }

    public List<PaymentEntity> listRecent(int limit) {
        return MapperUtils.selectList(paymentMapper, PaymentEntity.class,
                wrapper -> wrapper.orderByDesc("created_at").last("LIMIT " + limit));
    }
}
