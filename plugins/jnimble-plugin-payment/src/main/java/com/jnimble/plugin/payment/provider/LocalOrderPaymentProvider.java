package com.jnimble.plugin.payment.provider;

import com.jnimble.plugin.order.payment.OrderPaymentProvider;
import com.jnimble.plugin.order.payment.OrderPaymentRequest;
import com.jnimble.plugin.order.payment.OrderPaymentResult;
import com.jnimble.plugin.order.payment.OrderPaymentStatus;
import com.jnimble.plugin.payment.model.entity.PaymentEntity;
import com.jnimble.plugin.payment.service.PaymentService;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LocalOrderPaymentProvider implements OrderPaymentProvider {

    private static final Set<String> METHODS = Set.of("CASH");

    private final PaymentService paymentService;

    public LocalOrderPaymentProvider(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public boolean supports(String method) {
        return method != null && METHODS.contains(method.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public OrderPaymentResult pay(OrderPaymentRequest request) {
        PaymentEntity payment = paymentService.processPayment(
                String.valueOf(request.orderId()),
                request.method(),
                request.amount(),
                request.receivedAmount(),
                request.operator(),
                request.idempotencyKey(),
                request
        );
        return new OrderPaymentResult(
                OrderPaymentStatus.SUCCEEDED,
                payment.getId(),
                payment.getProviderTradeNo(),
                payment.getAmount(),
                payment.getChangeAmount(),
                null,
                null
        );
    }
}
