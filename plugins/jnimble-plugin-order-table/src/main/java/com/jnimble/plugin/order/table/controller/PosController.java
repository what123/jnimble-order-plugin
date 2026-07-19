package com.jnimble.plugin.order.table.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.table.model.dto.PosModels.AddItemRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.BatchAddItemsRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.ConfirmationActionRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.GuestCountRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.OpenSessionRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.QuantityRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.ReturnConfirmationRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.TableActionRequest;
import com.jnimble.plugin.order.table.service.TableOrderService;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/plugins/order-table")
public class PosController {

    private final TableOrderService tableOrderService;
    private final ControllerAuthorization authorization;

    public PosController(TableOrderService tableOrderService,
                         ControllerAuthorization authorization) {
        this.tableOrderService = tableOrderService;
        this.authorization = authorization;
    }

    @GetMapping("/pos")
    public String posLayout(Map<String, Object> model, Principal principal) {
        authorization.requirePermission("order-table.pos.view");
        model.put("operator", principal == null ? "system" : principal.getName());
        model.put("canConfirmOrders", authorization.hasPermission("order-table.pos.confirm"));
        return "plugin/order-table/pos/layout";
    }

    @GetMapping("/pos/board")
    @ResponseBody
    public Object board() {
        authorization.requirePermission("order-table.pos.view");
        return tableOrderService.board();
    }

    @GetMapping("/pos/catalog")
    @ResponseBody
    public Object catalog() {
        authorization.requirePermission("order-table.pos.view");
        return tableOrderService.catalog();
    }

    @GetMapping("/pos/confirmations")
    @ResponseBody
    public Object pendingConfirmations() {
        authorization.requirePermission("order-table.pos.view");
        return tableOrderService.pendingConfirmations();
    }

    @PostMapping("/pos/confirmations/{submissionId}/confirm")
    @ResponseBody
    public Object confirmSubmission(@PathVariable Long submissionId,
                                    @RequestBody ConfirmationActionRequest request) {
        authorization.requirePermission("order-table.pos.confirm");
        return tableOrderService.confirmSubmission(submissionId, request.operator());
    }

    @PostMapping("/pos/confirmations/{submissionId}/return")
    @ResponseBody
    public Object returnSubmission(@PathVariable Long submissionId,
                                   @RequestBody ReturnConfirmationRequest request) {
        authorization.requirePermission("order-table.pos.confirm");
        return tableOrderService.returnSubmission(
                submissionId, request.operator(), request.reason()
        );
    }

    @GetMapping("/pos/sessions/{sessionId}")
    @ResponseBody
    public Object session(@PathVariable Long sessionId) {
        authorization.requirePermission("order-table.pos.view");
        return tableOrderService.getSession(sessionId);
    }

    @PostMapping(value = "/pos/tables/{tableId}/open", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object openTable(@PathVariable Long tableId, @RequestBody OpenSessionRequest request) {
        authorization.requirePermission("order-table.pos.open");
        return tableOrderService.openTable(
                tableId, request.guestCount(), request.operator(), request.remark(), false
        );
    }

    @PostMapping(value = "/pos/tables/{tableId}/share", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object shareTable(@PathVariable Long tableId, @RequestBody OpenSessionRequest request) {
        authorization.requirePermission("order-table.pos.open");
        return tableOrderService.openTable(
                tableId, request.guestCount(), request.operator(), request.remark(), true
        );
    }

    @PostMapping(value = "/pos/tables/{tableId}/open",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public Map<String, Object> openTableLegacy(@PathVariable Long tableId,
                                               @RequestParam Integer partySize,
                                               @RequestParam(defaultValue = "system") String operator) {
        authorization.requirePermission("order-table.pos.open");
        OrderEntity order = tableOrderService.openTable(tableId, partySize, operator);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("sessionId", order.getSessionId());
        return result;
    }

    @PostMapping("/pos/sessions/{sessionId}/items")
    @ResponseBody
    public Object addItem(@PathVariable Long sessionId, @RequestBody AddItemRequest request) {
        authorization.requirePermission("order-table.pos.view");
        return tableOrderService.addMenuItem(sessionId, request);
    }

    @PostMapping("/pos/sessions/{sessionId}/batch-items")
    @ResponseBody
    public Object batchAddItems(@PathVariable Long sessionId,
                                @RequestBody BatchAddItemsRequest request) {
        authorization.requirePermission("order-table.pos.view");
        return tableOrderService.batchAddMenuItems(sessionId, request);
    }

    @PostMapping("/pos/sessions/{sessionId}/items/{itemId}/quantity")
    @ResponseBody
    public Object updateItemQuantity(@PathVariable Long sessionId,
                                     @PathVariable Long itemId,
                                     @RequestBody QuantityRequest request) {
        authorization.requirePermission("order-table.pos.view");
        return tableOrderService.updateItemQuantity(sessionId, itemId, request.quantity());
    }

    @DeleteMapping("/pos/sessions/{sessionId}/items/{itemId}")
    @ResponseBody
    public Object removeItem(@PathVariable Long sessionId, @PathVariable Long itemId) {
        authorization.requirePermission("order-table.pos.view");
        return tableOrderService.removeItem(sessionId, itemId);
    }

    @PostMapping("/pos/sessions/{sessionId}/confirm")
    @ResponseBody
    public Object confirmOrder(@PathVariable Long sessionId) {
        authorization.requirePermission("order-table.pos.view");
        return tableOrderService.confirmOrder(sessionId);
    }

    @PostMapping("/pos/sessions/{sessionId}/guest-count")
    @ResponseBody
    public Object updateGuestCount(@PathVariable Long sessionId,
                                   @RequestBody GuestCountRequest request) {
        authorization.requirePermission("order-table.pos.open");
        return tableOrderService.updateGuestCount(
                sessionId, request.guestCount(), request.operator()
        );
    }

    @PostMapping("/pos/sessions/{sessionId}/combine")
    @ResponseBody
    public Object combineTable(@PathVariable Long sessionId,
                               @RequestBody TableActionRequest request) {
        authorization.requirePermission("order-table.pos.open");
        return tableOrderService.combineTable(sessionId, request.tableId(), request.operator());
    }

    @PostMapping("/pos/sessions/{sessionId}/transfer")
    @ResponseBody
    public Object transferTable(@PathVariable Long sessionId,
                                @RequestBody TableActionRequest request) {
        authorization.requirePermission("order-table.pos.open");
        return tableOrderService.transferTable(sessionId, request.tableId(), request.operator());
    }

    @PostMapping("/pos/sessions/{sessionId}/checkout")
    @ResponseBody
    public Object checkout(@PathVariable Long sessionId, @RequestBody CheckoutRequest request) {
        authorization.requirePermission("order-table.pos.settle");
        return tableOrderService.checkout(sessionId, request);
    }

    @PostMapping("/pos/tables/{tableId}/cleaning/complete")
    @ResponseBody
    public Map<String, Object> completeCleaning(@PathVariable Long tableId,
                                                @RequestParam(defaultValue = "system") String operator) {
        authorization.requirePermission("order-table.pos.open");
        tableOrderService.completeTurnover(tableId, operator);
        return Map.of("success", true);
    }

    @GetMapping("/pos/settle-panel")
    public String settlePanel() {
        return "plugin/order-table/pos/settle-panel";
    }

    @PostMapping(value = "/pos/orders/{orderId}/settle",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public Map<String, Object> settleOrderLegacy(@PathVariable Long orderId,
                                                 @RequestParam String paymentMethod,
                                                 @RequestParam BigDecimal finalAmount,
                                                 @RequestParam(required = false) BigDecimal receivedAmount) {
        authorization.requirePermission("order-table.pos.settle");
        return tableOrderService.settleOrder(
                orderId, paymentMethod, finalAmount, receivedAmount
        );
    }
}
