package com.jnimble.plugin.order.payment;

import java.math.BigDecimal;

public record OrderPaymentResult(
        OrderPaymentStatus status,
        String paymentId,
        String providerTradeNo,
        BigDecimal paidAmount,
        BigDecimal changeAmount,
        String failureCode,
        String failureMessage
) {
    public boolean succeeded() {
        return status == OrderPaymentStatus.SUCCEEDED;
    }
}
