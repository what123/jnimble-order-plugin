package com.jnimble.plugin.order.table.service;

import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecGroupEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecOptionEntity;
import com.jnimble.plugin.menu.service.CategoryService;
import com.jnimble.plugin.menu.service.MenuItemService;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.payment.OrderPaymentProviderRegistry;
import com.jnimble.plugin.order.payment.OrderPaymentRequest;
import com.jnimble.plugin.order.payment.OrderPaymentResult;
import com.jnimble.plugin.order.payment.OrderPaymentStatus;
import com.jnimble.plugin.order.service.OrderService;
import com.jnimble.plugin.order.service.OrderSubmissionService;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.submission.OrderSubmissionView;
import com.jnimble.plugin.order.table.model.dto.PosModels.AddItemRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.BatchAddItemEntry;
import com.jnimble.plugin.order.table.model.dto.PosModels.BatchAddItemsRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.CatalogResponse;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutResult;
import com.jnimble.plugin.order.table.model.dto.PosModels.PendingConfirmation;
import com.jnimble.plugin.order.table.model.dto.PosModels.PendingConfirmationItem;
import com.jnimble.plugin.order.table.model.dto.PosModels.SessionDetail;
import com.jnimble.plugin.order.table.model.dto.PosModels.TableBoardItem;
import com.jnimble.plugin.order.table.model.entity.CheckoutEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TableOrderService {

    private final DiningSessionService diningSessionService;
    private final OrderService orderService;
    private final OrderSubmissionService submissionService;
    private final MenuItemService menuItemService;
    private final CategoryService categoryService;
    private final OrderPaymentProviderRegistry paymentProviders;

    public TableOrderService(DiningSessionService diningSessionService,
                             OrderService orderService,
                             OrderSubmissionService submissionService,
                             MenuItemService menuItemService,
                             CategoryService categoryService,
                             OrderPaymentProviderRegistry paymentProviders) {
        this.diningSessionService = diningSessionService;
        this.orderService = orderService;
        this.submissionService = submissionService;
        this.menuItemService = menuItemService;
        this.categoryService = categoryService;
        this.paymentProviders = paymentProviders;
    }

    public List<TableBoardItem> board() {
        return diningSessionService.board();
    }

    public CatalogResponse catalog() {
        List<CategoryEntity> categories = categoryService.listCategories().stream()
                .filter(category -> "ENABLED".equals(category.getStatus()))
                .toList();
        return new CatalogResponse(categories, menuItemService.listItems(null, null, "ENABLED"));
    }

    public SessionDetail getSession(Long sessionId) {
        return diningSessionService.getDetail(sessionId);
    }

    public SessionDetail openTable(Long tableId, Integer guestCount, String operator,
                                   String remark, boolean shared) {
        return diningSessionService.openTable(tableId, guestCount, operator, remark, shared);
    }

    /**
     * Compatibility entry point retained for callers of the original POS controller.
     */
    public OrderEntity openTable(Long tableId, Integer partySize, String operator) {
        return openTable(tableId, partySize, operator, null, false).order();
    }

    public SessionDetail batchAddMenuItems(Long sessionId, BatchAddItemsRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Items are required");
        }
        SessionDetail detail = requireEditableSession(sessionId);
        requireNoPendingSubmission(detail.order().getId());
        List<OrderItemEntity> orderItems = new ArrayList<>();
        for (BatchAddItemEntry entry : request.items()) {
            if (entry.menuItemId() == null) {
                throw new IllegalArgumentException("Menu item is required");
            }
            MenuItemEntity menuItem = menuItemService.getItem(entry.menuItemId());
            if (!"ENABLED".equals(menuItem.getStatus())) {
                throw new IllegalStateException("Menu item is not available: " + menuItem.getName());
            }
            SpecificationPrice specPrice = resolveSpecification(
                    menuItem,
                    entry.specOptionIds() == null ? List.of() : entry.specOptionIds()
            );
            BigDecimal unitPrice = menuItem.getPrice().add(specPrice.priceAdjustment());
            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setMenuItemId(menuItem.getId());
            orderItem.setItemName(menuItem.getName());
            orderItem.setSpecification(specPrice.description());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(entry.quantity() == null ? 1 : entry.quantity());
            orderItem.setRemark(trimToNull(entry.remark()));
            orderItems.add(orderItem);
        }
        orderService.batchAddItems(detail.order().getId(), orderItems);
        return getSession(sessionId);
    }

    public SessionDetail addMenuItem(Long sessionId, AddItemRequest request) {
        if (request == null || request.menuItemId() == null) {
            throw new IllegalArgumentException("Menu item is required");
        }
        SessionDetail detail = requireEditableSession(sessionId);
        requireNoPendingSubmission(detail.order().getId());
        MenuItemEntity menuItem = menuItemService.getItem(request.menuItemId());
        if (!"ENABLED".equals(menuItem.getStatus())) {
            throw new IllegalStateException("Menu item is not available");
        }
        SpecificationPrice specificationPrice = resolveSpecification(
                menuItem,
                request.specOptionIds() == null ? List.of() : request.specOptionIds()
        );
        BigDecimal unitPrice = menuItem.getPrice().add(specificationPrice.priceAdjustment());
        orderService.addItem(
                detail.order().getId(),
                menuItem.getId(),
                menuItem.getName(),
                specificationPrice.description(),
                unitPrice,
                request.quantity() == null ? 1 : request.quantity(),
                trimToNull(request.remark())
        );
        return getSession(sessionId);
    }

    public SessionDetail updateItemQuantity(Long sessionId, Long itemId, Integer quantity) {
        SessionDetail detail = requireEditableSession(sessionId);
        requireNoPendingSubmission(detail.order().getId());
        orderService.updateItemQuantity(detail.order().getId(), itemId, quantity);
        return getSession(sessionId);
    }

    public SessionDetail removeItem(Long sessionId, Long itemId) {
        SessionDetail detail = requireEditableSession(sessionId);
        requireNoPendingSubmission(detail.order().getId());
        orderService.removeItem(detail.order().getId(), itemId);
        return getSession(sessionId);
    }

    public SessionDetail confirmOrder(Long sessionId) {
        SessionDetail detail = requireEditableSession(sessionId);
        requireNoPendingSubmission(detail.order().getId());
        orderService.confirmOrder(detail.order().getId());
        return getSession(sessionId);
    }

    public List<PendingConfirmation> pendingConfirmations() {
        return submissionService.listPending().stream()
                .filter(view -> view.order().getSessionId() != null)
                .map(this::toPendingConfirmation)
                .toList();
    }

    public SessionDetail confirmSubmission(Long submissionId, String operator) {
        OrderSubmissionView view = submissionService.confirm(submissionId, normalizeOperator(operator));
        return getSession(view.order().getSessionId());
    }

    public SessionDetail returnSubmission(Long submissionId, String operator, String reason) {
        OrderSubmissionView view = submissionService.returnSubmission(
                submissionId, normalizeOperator(operator), reason
        );
        return getSession(view.order().getSessionId());
    }

    public SessionDetail updateGuestCount(Long sessionId, Integer guestCount, String operator) {
        return diningSessionService.updateGuestCount(sessionId, guestCount, operator);
    }

    public SessionDetail combineTable(Long sessionId, Long tableId, String operator) {
        return diningSessionService.combineTable(sessionId, tableId, operator);
    }

    public SessionDetail transferTable(Long sessionId, Long tableId, String operator) {
        return diningSessionService.transferTable(sessionId, tableId, operator);
    }

    public void completeTurnover(Long tableId, String operator) {
        diningSessionService.completeTurnover(tableId, operator);
    }

    public CheckoutResult checkout(Long sessionId, CheckoutRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Checkout request is required");
        }
        String operator = normalizeOperator(request.operator());
        SessionDetail detail = getSession(sessionId);
        if (detail.order() != null) {
            requireNoPendingSubmission(detail.order().getId());
        }
        CheckoutEntity checkout = diningSessionService.beginCheckout(
                sessionId, operator, request.idempotencyKey()
        );
        if ("PAID".equals(checkout.getStatus())) {
            return toCheckoutResult(checkout);
        }
        OrderEntity order = orderService.getOrder(checkout.getOrderId());
        OrderPaymentRequest paymentRequest = new OrderPaymentRequest(
                UUID.randomUUID().toString(),
                checkout.getCheckoutNo(),
                order.getId(),
                order.getOrderNo(),
                checkout.getIdempotencyKey(),
                request.method(),
                checkout.getPayableAmount(),
                request.receivedAmount(),
                operator,
                Map.of("sessionId", sessionId, "tableId", order.getTableId())
        );

        OrderPaymentResult paymentResult;
        try {
            paymentResult = paymentProviders.pay(paymentRequest);
        } catch (RuntimeException exception) {
            diningSessionService.markCheckoutFailed(
                    checkout.getCheckoutNo(), operator, exception.getMessage()
            );
            throw exception;
        }
        if (paymentResult.status() == OrderPaymentStatus.PENDING) {
            return diningSessionService.markCheckoutPending(
                    checkout.getCheckoutNo(), request.method()
            );
        }
        if (paymentResult.status() == OrderPaymentStatus.FAILED) {
            diningSessionService.markCheckoutFailed(
                    checkout.getCheckoutNo(), operator, paymentResult.failureMessage()
            );
            throw new IllegalStateException(paymentResult.failureMessage() == null
                    ? "Payment failed" : paymentResult.failureMessage());
        }
        return diningSessionService.completeCheckout(
                checkout.getCheckoutNo(),
                request.method(),
                request.receivedAmount(),
                paymentResult,
                operator
        );
    }

    public Map<String, Object> settleOrder(Long orderId, String paymentMethod,
                                           BigDecimal finalAmount, BigDecimal receivedAmount) {
        OrderEntity order = orderService.getOrder(orderId);
        CheckoutResult checkout = checkout(order.getSessionId(), new CheckoutRequest(
                paymentMethod,
                receivedAmount,
                order.getOperator(),
                UUID.randomUUID().toString().replace("-", "")
        ));
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("finalAmount", checkout.payableAmount());
        result.put("changeAmount", checkout.changeAmount());
        return result;
    }

    private SessionDetail requireEditableSession(Long sessionId) {
        SessionDetail detail = getSession(sessionId);
        if (!"DINING".equals(detail.session().getStatus())) {
            throw new IllegalStateException("Dining session is not editable");
        }
        if (detail.order() == null) {
            throw new IllegalStateException("Dining session has no active order");
        }
        return detail;
    }

    private void requireNoPendingSubmission(Long orderId) {
        if (submissionService.hasPendingForOrder(orderId)) {
            throw new IllegalStateException("Order has a submission waiting for staff confirmation");
        }
    }

    private SpecificationPrice resolveSpecification(MenuItemEntity item, List<Long> optionIds) {
        Set<Long> selectedIds = new LinkedHashSet<>(optionIds);
        Set<Long> consumedIds = new HashSet<>();
        List<String> descriptions = new ArrayList<>();
        BigDecimal adjustment = BigDecimal.ZERO;
        for (MenuItemSpecGroupEntity group : safeGroups(item)) {
            List<MenuItemSpecOptionEntity> selected = safeOptions(group).stream()
                    .filter(option -> "ENABLED".equals(option.getStatus()))
                    .filter(option -> selectedIds.contains(option.getId()))
                    .toList();
            if (Boolean.TRUE.equals(group.getRequired()) && selected.isEmpty()) {
                throw new IllegalArgumentException("Please select " + group.getName());
            }
            if (!Boolean.TRUE.equals(group.getMulti()) && selected.size() > 1) {
                throw new IllegalArgumentException(group.getName() + " only allows one option");
            }
            if (!selected.isEmpty()) {
                descriptions.add(group.getName() + ": " + selected.stream()
                        .map(MenuItemSpecOptionEntity::getName)
                        .reduce((left, right) -> left + "/" + right)
                        .orElse(""));
            }
            for (MenuItemSpecOptionEntity option : selected) {
                consumedIds.add(option.getId());
                if (option.getPriceAdjust() != null) {
                    adjustment = adjustment.add(option.getPriceAdjust());
                }
            }
        }
        if (!consumedIds.equals(selectedIds)) {
            throw new IllegalArgumentException("Selected specification does not belong to the menu item");
        }
        return new SpecificationPrice(
                descriptions.isEmpty() ? null : String.join("; ", descriptions),
                adjustment
        );
    }

    private List<MenuItemSpecGroupEntity> safeGroups(MenuItemEntity item) {
        return item.getGroups() == null ? List.of() : item.getGroups();
    }

    private List<MenuItemSpecOptionEntity> safeOptions(MenuItemSpecGroupEntity group) {
        return group.getOptions() == null ? List.of() : group.getOptions();
    }

    private CheckoutResult toCheckoutResult(CheckoutEntity checkout) {
        return new CheckoutResult(
                checkout.getCheckoutNo(),
                checkout.getStatus(),
                checkout.getSessionId(),
                checkout.getOrderId(),
                checkout.getPayableAmount(),
                checkout.getPaidAmount(),
                checkout.getChangeAmount(),
                checkout.getPaymentId()
        );
    }

    private PendingConfirmation toPendingConfirmation(OrderSubmissionView view) {
        SessionDetail detail = getSession(view.order().getSessionId());
        List<PendingConfirmationItem> items = view.items().stream()
                .filter(item -> !"CANCELLED".equals(item.getStatus()))
                .map(item -> new PendingConfirmationItem(
                        String.valueOf(item.getId()),
                        item.getItemName(),
                        item.getSpecification(),
                        item.getRemark(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .toList();
        BigDecimal amount = items.stream()
                .map(PendingConfirmationItem::subtotal)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int quantity = items.stream()
                .map(PendingConfirmationItem::quantity)
                .filter(value -> value != null)
                .reduce(0, Integer::sum);
        return new PendingConfirmation(
                String.valueOf(view.submission().getId()),
                String.valueOf(view.order().getSessionId()),
                String.valueOf(view.order().getId()),
                view.order().getOrderNo(),
                detail.occupiedTables().stream().map(table -> String.valueOf(table.getId())).toList(),
                detail.occupiedTables().stream()
                        .map(table -> table.getTableName() == null ? table.getCode() : table.getTableName())
                        .toList(),
                view.submission().getBatchSeq(),
                view.submission().getBatchType(),
                view.submission().getSourceType(),
                view.submission().getStatus(),
                view.submission().getSubmittedAt(),
                amount,
                items.size(),
                quantity,
                items
        );
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record SpecificationPrice(String description, BigDecimal priceAdjustment) {
    }
}
