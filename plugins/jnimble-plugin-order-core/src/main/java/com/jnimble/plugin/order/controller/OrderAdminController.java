package com.jnimble.plugin.order.controller;

import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.service.OrderService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/plugins/order-core")
public class OrderAdminController {

    private final OrderService orderService;

    public OrderAdminController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders/list")
    @ResponseBody
    public List<OrderEntity> listOrders(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long tableId) {
        LocalDateTime dateFrom = LocalDate.now().atStartOfDay();
        LocalDateTime dateTo = LocalDate.now().atTime(LocalTime.MAX);
        return orderService.listOrders(status, tableId, dateFrom, dateTo);
    }

    @GetMapping("/orders")
    public String ordersPage() {
        return "plugin/order-core/admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetailPage(@PathVariable Long id, Map<String, Object> model) {
        model.put("order", orderService.getOrder(id));
        model.put("items", orderService.getOrderItems(id));
        return "plugin/order-core/admin/order-detail";
    }
}
