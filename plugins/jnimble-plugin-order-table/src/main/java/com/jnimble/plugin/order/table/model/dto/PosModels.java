package com.jnimble.plugin.order.table.model.dto;

import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.table.model.entity.DiningSessionEntity;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PosModels {

    private PosModels() {
    }

    public record SessionSummary(
            @JsonSerialize(using = ToStringSerializer.class)
            Long sessionId,
            String sessionNo,
            Integer guestCount,
            String status,
            @JsonSerialize(using = ToStringSerializer.class)
            Long orderId,
            String orderNo,
            BigDecimal amount,
            LocalDateTime openedAt
    ) {
    }

    public record TableBoardItem(
            @JsonSerialize(using = ToStringSerializer.class)
            Long tableId,
            String tableName,
            String area,
            Integer minPartySize,
            Integer maxPartySize,
            String scanCode,
            String status,
            Integer occupiedSeats,
            Integer availableSeats,
            List<SessionSummary> sessions
    ) {
    }

    public record SessionDetail(
            DiningSessionEntity session,
            TableEntity primaryTable,
            List<TableEntity> occupiedTables,
            OrderEntity order,
            List<OrderItemEntity> items
    ) {
    }

    public record CatalogResponse(
            List<CategoryEntity> categories,
            List<MenuItemEntity> items
    ) {
    }

    public record OpenSessionRequest(
            Integer guestCount,
            String operator,
            String remark
    ) {
    }

    public record AddItemRequest(
            Long menuItemId,
            List<Long> specOptionIds,
            Integer quantity,
            String remark
    ) {
    }

    public record BatchAddItemEntry(
            Long menuItemId,
            List<Long> specOptionIds,
            Integer quantity,
            String remark
    ) {
    }

    public record BatchAddItemsRequest(
            List<BatchAddItemEntry> items
    ) {
    }

    public record QuantityRequest(Integer quantity) {
    }

    public record GuestCountRequest(Integer guestCount, String operator) {
    }

    public record TableActionRequest(Long tableId, String operator) {
    }

    public record CheckoutRequest(
            String method,
            BigDecimal receivedAmount,
            String operator,
            String idempotencyKey
    ) {
    }

    public record CheckoutResult(
            String checkoutNo,
            String status,
            @JsonSerialize(using = ToStringSerializer.class)
            Long sessionId,
            @JsonSerialize(using = ToStringSerializer.class)
            Long orderId,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            BigDecimal changeAmount,
            String paymentId
    ) {
    }

    public record PendingConfirmationItem(
            String itemId,
            String itemName,
            String specification,
            String remark,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }

    public record PendingConfirmation(
            String submissionId,
            String sessionId,
            String orderId,
            String orderNo,
            List<String> tableIds,
            List<String> tableNames,
            Integer batchSeq,
            String batchType,
            String sourceType,
            String status,
            LocalDateTime submittedAt,
            BigDecimal amount,
            Integer itemKinds,
            Integer quantity,
            List<PendingConfirmationItem> items
    ) {
    }

    public record ConfirmationActionRequest(String operator) {
    }

    public record ReturnConfirmationRequest(String operator, String reason) {
    }
}
