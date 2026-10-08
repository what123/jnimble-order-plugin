package com.jnimble.plugin.app.controller;

import com.jnimble.plugin.app.model.dto.ApiResult;
import com.jnimble.plugin.app.service.AppAuthService;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.BatchRequest;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.ProductionBatchRequest;
import com.jnimble.plugin.order.service.KitchenQueueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/kitchen")
public class AppKitchenController {

    private static final String KITCHEN_VIEW = "order-core.kitchen.view";
    private static final String KITCHEN_OPERATE = "order-core.kitchen.operate";
    private static final String KITCHEN_PRINT = "order-core.kitchen.print";

    private final KitchenQueueService kitchenQueueService;
    private final AppAuthService authService;

    public AppKitchenController(KitchenQueueService kitchenQueueService, AppAuthService authService) {
        this.kitchenQueueService = kitchenQueueService;
        this.authService = authService;
    }

    @GetMapping("/queue")
    public Map<String, Object> queue(HttpServletRequest request, HttpServletResponse response,
                                     @RequestParam(defaultValue = "WAITING") String status,
                                     @RequestParam(defaultValue = "PAPERLESS") String dispatchMode,
                                     @RequestParam(defaultValue = "false") boolean grouped,
                                     @RequestParam(defaultValue = "1") int page) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, KITCHEN_VIEW);
            return ApiResult.success(kitchenQueueService.list(status, dispatchMode, grouped, page));
        });
    }

    @PostMapping("/queue/{id}/start")
    public Map<String, Object> start(HttpServletRequest request, HttpServletResponse response,
                                     @PathVariable Long id) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, KITCHEN_OPERATE);
            kitchenQueueService.start(id);
            return ApiResult.success(new LinkedHashMap<>());
        });
    }

    @PostMapping("/queue/groups/start")
    public Map<String, Object> startGroup(HttpServletRequest request, HttpServletResponse response,
                                          @RequestBody(required = false) BatchRequest body) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, KITCHEN_OPERATE);
            kitchenQueueService.startGroup(body == null ? null : body.queueItemIds());
            return ApiResult.success(new LinkedHashMap<>());
        });
    }

    @PostMapping("/queue/{id}/complete")
    public Map<String, Object> complete(HttpServletRequest request, HttpServletResponse response,
                                        @PathVariable Long id) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, KITCHEN_OPERATE);
            kitchenQueueService.complete(id);
            return ApiResult.success(new LinkedHashMap<>());
        });
    }

    @PostMapping("/production-batches/complete")
    public Map<String, Object> completeProductionBatches(HttpServletRequest request, HttpServletResponse response,
                                                         @RequestBody(required = false) ProductionBatchRequest body) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, KITCHEN_OPERATE);
            kitchenQueueService.completeProductionBatches(body == null ? null : body.productionBatchNos());
            return ApiResult.success(new LinkedHashMap<>());
        });
    }

    @PostMapping("/orders/{orderId}/print")
    public Map<String, Object> print(HttpServletRequest request, HttpServletResponse response,
                                     @PathVariable Long orderId) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, KITCHEN_PRINT);
            return ApiResult.success(kitchenQueueService.printOrder(orderId));
        });
    }
}
