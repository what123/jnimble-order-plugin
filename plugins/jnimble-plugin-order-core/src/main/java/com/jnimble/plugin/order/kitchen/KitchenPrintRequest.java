package com.jnimble.plugin.order.kitchen;

import com.jnimble.plugin.order.model.entity.KitchenQueueItemEntity;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import java.util.List;

public record KitchenPrintRequest(
        OrderEntity order,
        List<KitchenQueueItemEntity> items,
        String ticketNumber
) {
    public KitchenPrintRequest {
        items = List.copyOf(items);
    }
}

