package com.jnimble.plugin.app.controller;

import com.jnimble.plugin.app.model.dto.ApiResult;
import com.jnimble.plugin.app.service.AppAuthService;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/orders")
public class AppOrderController {

    private static final String ORDER_VIEW = "order-core.admin.view";

    private final OrderService orderService;
    private final AppAuthService authService;

    public AppOrderController(OrderService orderService, AppAuthService authService) {
        this.orderService = orderService;
        this.authService = authService;
    }

    @GetMapping
    public Map<String, Object> listOrders(HttpServletRequest request, HttpServletResponse response,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long tableId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, ORDER_VIEW);
            LocalDate from = dateFrom == null ? LocalDate.now() : dateFrom;
            LocalDate to = dateTo == null ? from : dateTo;
            LocalDateTime fromTime = from.atStartOfDay();
            LocalDateTime toTime = to.atTime(LocalTime.MAX);
            List<OrderEntity> orders = orderService.listOrders(status, tableId, fromTime, toTime);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orders", orders);
            return ApiResult.success(data);
        });
    }

    @GetMapping("/{id}")
    public Map<String, Object> orderDetail(HttpServletRequest request, HttpServletResponse response,
                                           @PathVariable Long id) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, ORDER_VIEW);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("order", orderService.getOrder(id));
            data.put("items", orderService.getOrderItems(id));
            return ApiResult.success(data);
        });
    }
}
