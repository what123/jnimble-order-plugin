package com.jnimble.plugin.order.submission;

import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.model.entity.OrderSubmissionEntity;
import java.util.List;

public record OrderSubmissionView(
        OrderSubmissionEntity submission,
        OrderEntity order,
        List<OrderItemEntity> items
) {
}
