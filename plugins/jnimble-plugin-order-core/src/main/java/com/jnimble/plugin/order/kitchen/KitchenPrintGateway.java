package com.jnimble.plugin.order.kitchen;

@FunctionalInterface
public interface KitchenPrintGateway {

    KitchenPrintResult print(KitchenPrintRequest request);
}

