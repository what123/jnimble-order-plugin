package com.jnimble.plugin.order.controller;

import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.service.OrderService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAdminControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderAdminController orderAdminController;

    @Test
    void testOrdersPage() {
        assertEquals("plugin/order-core/admin/orders", orderAdminController.ordersPage());
    }

    @Test
    void testListOrders() {
        OrderEntity order = new OrderEntity();
        order.setId(1L);
        order.setStatus("CONFIRMED");
        when(orderService.listOrders(eq("CONFIRMED"), eq(1L),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(order));

        List<OrderEntity> result = orderAdminController.listOrders("CONFIRMED", 1L);

        assertEquals(List.of(order), result);
    }

    @Test
    void testOrderDetailPage() {
        OrderEntity order = new OrderEntity();
        order.setId(1L);
        OrderItemEntity item = new OrderItemEntity();
        item.setOrderId(1L);
        when(orderService.getOrder(1L)).thenReturn(order);
        when(orderService.getOrderItems(1L)).thenReturn(List.of(item));
        Map<String, Object> model = new HashMap<>();

        String view = orderAdminController.orderDetailPage(1L, model);

        assertEquals("plugin/order-core/admin/order-detail", view);
        assertNotNull(model.get("order"));
        assertNotNull(model.get("items"));
        verify(orderService).getOrder(1L);
        verify(orderService).getOrderItems(1L);
    }
}
