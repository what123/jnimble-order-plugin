package com.jnimble.plugin.order.payment;

import java.math.BigDecimal;
import java.util.Map;

public record OrderPaymentRequest(
        String eventId,
        String checkoutNo,
        Long orderId,
        String orderNo,
        String idempotencyKey,
        String method,
        BigDecimal amount,
        BigDecimal receivedAmount,
        String operator,
        Map<String, Object> metadata
) {
}
