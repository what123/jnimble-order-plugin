package com.jnimble.plugin.app.controller;

import com.jnimble.plugin.app.model.dto.ApiResult;
import com.jnimble.plugin.app.service.AppAuthService;
import com.jnimble.plugin.order.table.model.dto.PosModels.AddItemRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.BatchAddItemsRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.GuestCountRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.OpenSessionRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.QuantityRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.TableActionRequest;
import com.jnimble.plugin.order.table.service.TableOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/pos")
public class AppPosController {

    private static final String POS_VIEW = "order-table.pos.view";
    private static final String POS_OPEN = "order-table.pos.open";
    private static final String POS_CONFIRM = "order-table.pos.confirm";
    private static final String POS_SETTLE = "order-table.pos.settle";

    private final TableOrderService tableOrderService;
    private final AppAuthService authService;

    public AppPosController(TableOrderService tableOrderService, AppAuthService authService) {
        this.tableOrderService = tableOrderService;
        this.authService = authService;
    }

    @GetMapping("/tables")
    public Map<String, Object> board(HttpServletRequest request, HttpServletResponse response) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, POS_VIEW);
            return ApiResult.success(tableOrderService.board());
        });
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog(HttpServletRequest request, HttpServletResponse response) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, POS_VIEW);
            return ApiResult.success(tableOrderService.catalog());
        });
    }

    @GetMapping("/confirmations")
    public Map<String, Object> pendingConfirmations(HttpServletRequest request, HttpServletResponse response) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, POS_VIEW);
            return ApiResult.success(tableOrderService.pendingConfirmations());
        });
    }

    @PostMapping("/confirmations/{submissionId}/confirm")
    public Map<String, Object> confirmSubmission(HttpServletRequest request, HttpServletResponse response,
                                                 @PathVariable Long submissionId) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.requirePermission(request, POS_CONFIRM);
            return ApiResult.success(tableOrderService.confirmSubmission(submissionId, session.username()));
        });
    }

    @PostMapping("/confirmations/{submissionId}/return")
    public Map<String, Object> returnSubmission(HttpServletRequest request, HttpServletResponse response,
                                                @PathVariable Long submissionId,
                                                @RequestBody(required = false) Map<String, String> body) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.requirePermission(request, POS_CONFIRM);
            String reason = body == null ? null : body.get("reason");
            return ApiResult.success(tableOrderService.returnSubmission(submissionId, session.username(), reason));
        });
    }

    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> session(HttpServletRequest request, HttpServletResponse response,
                                       @PathVariable Long sessionId) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, POS_VIEW);
            return ApiResult.success(tableOrderService.getSession(sessionId));
        });
    }

    @PostMapping("/tables/{tableId}/open")
    public Map<String, Object> openTable(HttpServletRequest request, HttpServletResponse response,
                                         @PathVariable Long tableId,
                                         @RequestBody(required = false) OpenSessionRequest body) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.requirePermission(request, POS_OPEN);
            OpenSessionRequest payload = body == null ? new OpenSessionRequest(null, null, null) : body;
            return ApiResult.success(tableOrderService.openTable(
                    tableId, payload.guestCount(), session.username(), payload.remark(), false));
        });
    }

    @PostMapping("/tables/{tableId}/share")
    public Map<String, Object> shareTable(HttpServletRequest request, HttpServletResponse response,
                                          @PathVariable Long tableId,
                                          @RequestBody(required = false) OpenSessionRequest body) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.requirePermission(request, POS_OPEN);
            OpenSessionRequest payload = body == null ? new OpenSessionRequest(null, null, null) : body;
            return ApiResult.success(tableOrderService.openTable(
                    tableId, payload.guestCount(), session.username(), payload.remark(), true));
        });
    }

    @PostMapping("/sessions/{sessionId}/items")
    public Map<String, Object> addItem(HttpServletRequest request, HttpServletResponse response,
                                       @PathVariable Long sessionId, @RequestBody AddItemRequest body) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, POS_VIEW);
            return ApiResult.success(tableOrderService.addMenuItem(sessionId, body));
        });
    }

    @PostMapping("/sessions/{sessionId}/batch-items")
    public Map<String, Object> batchAddItems(HttpServletRequest request, HttpServletResponse response,
                                             @PathVariable Long sessionId, @RequestBody BatchAddItemsRequest body) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, POS_VIEW);
            return ApiResult.success(tableOrderService.batchAddMenuItems(sessionId, body));
        });
    }

    @PostMapping("/sessions/{sessionId}/items/{itemId}/quantity")
    public Map<String, Object> updateItemQuantity(HttpServletRequest request, HttpServletResponse response,
                                                  @PathVariable Long sessionId, @PathVariable Long itemId,
                                                  @RequestBody QuantityRequest body) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, POS_VIEW);
            return ApiResult.success(tableOrderService.updateItemQuantity(sessionId, itemId, body.quantity()));
        });
    }

    @DeleteMapping("/sessions/{sessionId}/items/{itemId}")
    public Map<String, Object> removeItem(HttpServletRequest request, HttpServletResponse response,
                                          @PathVariable Long sessionId, @PathVariable Long itemId) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, POS_VIEW);
            return ApiResult.success(tableOrderService.removeItem(sessionId, itemId));
        });
    }

    @PostMapping("/sessions/{sessionId}/confirm")
    public Map<String, Object> confirmOrder(HttpServletRequest request, HttpServletResponse response,
                                            @PathVariable Long sessionId) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, POS_VIEW);
            return ApiResult.success(tableOrderService.confirmOrder(sessionId));
        });
    }

    @PostMapping("/sessions/{sessionId}/guest-count")
    public Map<String, Object> updateGuestCount(HttpServletRequest request, HttpServletResponse response,
                                                @PathVariable Long sessionId, @RequestBody GuestCountRequest body) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.requirePermission(request, POS_OPEN);
            return ApiResult.success(tableOrderService.updateGuestCount(
                    sessionId, body.guestCount(), session.username()));
        });
    }

    @PostMapping("/sessions/{sessionId}/combine")
    public Map<String, Object> combineTable(HttpServletRequest request, HttpServletResponse response,
                                            @PathVariable Long sessionId, @RequestBody TableActionRequest body) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.requirePermission(request, POS_OPEN);
            return ApiResult.success(tableOrderService.combineTable(sessionId, body.tableId(), session.username()));
        });
    }

    @PostMapping("/sessions/{sessionId}/transfer")
    public Map<String, Object> transferTable(HttpServletRequest request, HttpServletResponse response,
                                             @PathVariable Long sessionId, @RequestBody TableActionRequest body) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.requirePermission(request, POS_OPEN);
            return ApiResult.success(tableOrderService.transferTable(sessionId, body.tableId(), session.username()));
        });
    }

    @PostMapping("/sessions/{sessionId}/checkout")
    public Map<String, Object> checkout(HttpServletRequest request, HttpServletResponse response,
                                        @PathVariable Long sessionId, @RequestBody CheckoutRequest body) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.requirePermission(request, POS_SETTLE);
            CheckoutRequest payload = new CheckoutRequest(
                    body.method(), body.receivedAmount(), session.username(), body.idempotencyKey());
            return ApiResult.success(tableOrderService.checkout(sessionId, payload));
        });
    }

    @PostMapping("/tables/{tableId}/cleaning/complete")
    public Map<String, Object> completeCleaning(HttpServletRequest request, HttpServletResponse response,
                                                @PathVariable Long tableId) {
        return AppApiExecutor.guard(response, () -> {
            AppAuthService.AppSession session = authService.requirePermission(request, POS_OPEN);
            tableOrderService.completeTurnover(tableId, session.username());
            return ApiResult.success(new LinkedHashMap<>());
        });
    }
}
