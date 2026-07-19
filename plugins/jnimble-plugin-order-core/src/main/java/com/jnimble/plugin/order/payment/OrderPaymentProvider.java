package com.jnimble.plugin.order.payment;

public interface OrderPaymentProvider {

    boolean supports(String method);

    OrderPaymentResult pay(OrderPaymentRequest request);
}
