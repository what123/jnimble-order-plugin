package com.jnimble.plugin.order.kitchen;

public record KitchenTicketNumberContext(
        Long orderId,
        String orderNo,
        Long tableId,
        Long sessionId,
        String source
) {
}

