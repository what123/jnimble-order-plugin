package com.jnimble.plugin.order.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.BatchRequest;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.PrintResponse;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.ProductionBatchRequest;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.QueueResponse;
import com.jnimble.plugin.order.service.KitchenQueueService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping("/admin/plugins/order-core/kitchen")
public class KitchenQueueController {

    private final KitchenQueueService kitchenQueueService;
    private final ControllerAuthorization authorization;

    public KitchenQueueController(KitchenQueueService kitchenQueueService, ControllerAuthorization authorization) {
        this.kitchenQueueService = kitchenQueueService;
        this.authorization = authorization;
    }

    @GetMapping
    public String kitchenPage(Map<String, Object> model) {
        authorization.requirePermission("order-core.kitchen.view");
        model.put("activeNav", "order-kitchen");
        return "plugin/order-core/admin/kitchen-queue";
    }

    @GetMapping("/queue")
    @ResponseBody
    public QueueResponse queue(
            @RequestParam(defaultValue = "WAITING") String status,
            @RequestParam(defaultValue = "PAPERLESS") String dispatchMode,
            @RequestParam(defaultValue = "false") boolean grouped,
            @RequestParam(defaultValue = "1") int page
    ) {
        authorization.requirePermission("order-core.kitchen.view");
        return kitchenQueueService.list(status, dispatchMode, grouped, page);
    }

    @PostMapping("/queue/{id}/start")
    @ResponseBody
    public Map<String, Object> start(@PathVariable Long id) {
        authorization.requirePermission("order-core.kitchen.operate");
        kitchenQueueService.start(id);
        return Map.of("success", true);
    }

    @PostMapping("/queue/groups/start")
    @ResponseBody
    public Map<String, Object> startGroup(@RequestBody BatchRequest request) {
        authorization.requirePermission("order-core.kitchen.operate");
        kitchenQueueService.startGroup(request == null ? null : request.queueItemIds());
        return Map.of("success", true);
    }

    @PostMapping("/queue/{id}/complete")
    @ResponseBody
    public Map<String, Object> complete(@PathVariable Long id) {
        authorization.requirePermission("order-core.kitchen.operate");
        kitchenQueueService.complete(id);
        return Map.of("success", true);
    }

    @PostMapping("/queue/production-batches/complete")
    @ResponseBody
    public Map<String, Object> completeProductionBatches(@RequestBody ProductionBatchRequest request) {
        authorization.requirePermission("order-core.kitchen.operate");
        kitchenQueueService.completeProductionBatches(request == null ? null : request.productionBatchNos());
        return Map.of("success", true);
    }

    @PostMapping("/queue/orders/{orderId}/print")
    @ResponseBody
    public PrintResponse print(@PathVariable Long orderId) {
        authorization.requirePermission("order-core.kitchen.print");
        return kitchenQueueService.printOrder(orderId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, Object> handleInvalidRequest(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public Map<String, Object> handleInvalidState(IllegalStateException exception) {
        return Map.of("message", exception.getMessage());
    }
}
