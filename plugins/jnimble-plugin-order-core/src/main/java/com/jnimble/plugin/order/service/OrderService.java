package com.jnimble.plugin.order.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.mapper.OrderItemMapper;
import com.jnimble.plugin.order.mapper.OrderMapper;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final KitchenQueueService kitchenQueueService;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        KitchenQueueService kitchenQueueService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.kitchenQueueService = kitchenQueueService;
    }

    /**
     * Creates an order while retaining the legacy table association fields for ordering-mode plugins.
     * Validation and occupancy changes belong to the installed ordering-mode plugin.
     */
    public OrderEntity createOrder(Long tableId, Integer partySize, String operator) {
        return createOrder(tableId, partySize, operator, null, "LEGACY");
    }

    public OrderEntity createOrder(Long tableId, Integer partySize, String operator,
                                   Long sessionId, String source) {
        String prefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = MapperUtils.selectList(orderMapper, OrderEntity.class,
                wrapper -> wrapper.likeRight("order_no", "D" + prefix)).size();
        String orderNo = "D" + prefix + String.format("%04d", count + 1);

        OrderEntity entity = new OrderEntity();
        entity.setOrderNo(orderNo);
        entity.setTableId(tableId);
        entity.setSessionId(sessionId);
        entity.setPartySize(partySize);
        entity.setBusinessDate(LocalDate.now());
        entity.setSource(source == null || source.isBlank() ? "LEGACY" : source);
        entity.setVersion(0);
        entity.setStatus("DRAFT");
        entity.setTotalAmount(BigDecimal.ZERO);
        entity.setDiscountAmount(BigDecimal.ZERO);
        entity.setFinalAmount(BigDecimal.ZERO);
        entity.setOperator(operator);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        MapperUtils.insert(orderMapper, entity);

        return entity;
    }

    public OrderItemEntity addItem(Long orderId, Long menuItemId, String itemName,
                                   BigDecimal unitPrice, Integer quantity, String remark) {
        return addItem(orderId, menuItemId, itemName, null, unitPrice, quantity, remark, null);
    }

    public OrderItemEntity addItem(Long orderId, Long menuItemId, String itemName,
                                   String specification, BigDecimal unitPrice,
                                   Integer quantity, String remark) {
        return addItem(orderId, menuItemId, itemName, specification, unitPrice, quantity, remark, null);
    }

    @Transactional
    public OrderItemEntity addItem(Long orderId, Long menuItemId, String itemName,
                                   String specification, BigDecimal unitPrice,
                                   Integer quantity, String remark, Integer batchSeq) {
        OrderEntity order = requireOrderAcceptsNewItems(orderId);
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least one");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price must not be negative");
        }
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        OrderItemEntity item = new OrderItemEntity();
        item.setOrderId(orderId);
        item.setMenuItemId(menuItemId);
        item.setItemName(itemName);
        item.setSpecification(specification);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setSubtotal(subtotal);
        item.setRemark(remark);
        item.setStatus("NORMAL");
        item.setBatchSeq(batchSeq);
        item.setCreatedAt(LocalDateTime.now());

        MapperUtils.insert(orderItemMapper, item);
        recalculateOrder(orderId, order.getDiscountAmount());
        if ("CONFIRMED".equals(order.getStatus())) {
            kitchenQueueService.enqueue(order, List.of(item));
        }
        return item;
    }

    @Transactional
    public List<OrderItemEntity> batchAddItems(Long orderId, List<OrderItemEntity> items) {
        return addBatchItems(orderId, items, true);
    }

    @Transactional
    public List<OrderItemEntity> stageBatchItems(Long orderId, List<OrderItemEntity> items) {
        return addBatchItems(orderId, items, false);
    }

    private List<OrderItemEntity> addBatchItems(Long orderId, List<OrderItemEntity> items,
                                                boolean dispatchConfirmedOrder) {
        OrderEntity order = requireOrderAcceptsNewItemsForUpdate(orderId);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items must not be empty");
        }
        int nextBatchSeq = getMaxBatchSeq(orderId) + 1;
        List<OrderItemEntity> created = new ArrayList<>();
        for (OrderItemEntity item : items) {
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new IllegalArgumentException("Quantity must be at least one");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
                throw new IllegalArgumentException("Unit price must not be negative");
            }
            BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setOrderId(orderId);
            item.setSubtotal(subtotal);
            item.setStatus("NORMAL");
            item.setBatchSeq(nextBatchSeq);
            item.setCreatedAt(LocalDateTime.now());
            MapperUtils.insert(orderItemMapper, item);
            created.add(item);
        }
        recalculateOrder(orderId, order.getDiscountAmount());
        if (dispatchConfirmedOrder && "CONFIRMED".equals(order.getStatus())) {
            kitchenQueueService.enqueue(order, created);
        }
        return created;
    }

    @Transactional
    public List<OrderItemEntity> prepareInitialBatch(Long orderId) {
        OrderEntity order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        if (!"DRAFT".equals(order.getStatus())) {
            throw new IllegalStateException("Only a draft order can submit its initial batch");
        }
        List<OrderItemEntity> items = getActiveOrderItems(orderId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot submit an empty order");
        }
        for (OrderItemEntity item : items) {
            int batchSeq = item.getBatchSeq() == null ? 0 : item.getBatchSeq();
            if (batchSeq != 0 && batchSeq != 1) {
                throw new IllegalStateException("Draft order contains an invalid initial batch");
            }
            if (batchSeq == 0) {
                OrderItemEntity update = new OrderItemEntity();
                update.setId(item.getId());
                update.setBatchSeq(1);
                MapperUtils.updateById(orderItemMapper, update);
                item.setBatchSeq(1);
            }
        }
        return items;
    }

    @Transactional
    public List<OrderItemEntity> confirmBatch(Long orderId, Integer batchSeq) {
        if (batchSeq == null || batchSeq < 1) {
            throw new IllegalArgumentException("Batch sequence must be at least one");
        }
        OrderEntity order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        List<OrderItemEntity> items = getActiveOrderItemsByBatch(orderId, batchSeq);
        if (items.isEmpty()) {
            throw new IllegalStateException("Order batch has no active items");
        }
        if ("DRAFT".equals(order.getStatus())) {
            if (batchSeq != 1) {
                throw new IllegalStateException("A draft order can only confirm its initial batch");
            }
            OrderEntity update = new OrderEntity();
            update.setId(orderId);
            update.setStatus("CONFIRMED");
            update.setUpdatedAt(LocalDateTime.now());
            MapperUtils.updateById(orderMapper, update);
            order.setStatus("CONFIRMED");
        } else if (!"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException("Order can no longer confirm a batch");
        }
        kitchenQueueService.enqueue(order, items);
        return items;
    }

    @Transactional
    public List<OrderItemEntity> cancelAddOnBatch(Long orderId, Integer batchSeq) {
        if (batchSeq == null || batchSeq < 2) {
            throw new IllegalArgumentException("Only an add-on batch can be cancelled");
        }
        OrderEntity order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        if (!"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException("Add-on batches require a confirmed order");
        }
        List<OrderItemEntity> items = getActiveOrderItemsByBatch(orderId, batchSeq);
        for (OrderItemEntity item : items) {
            OrderItemEntity update = new OrderItemEntity();
            update.setId(item.getId());
            update.setStatus("CANCELLED");
            MapperUtils.updateById(orderItemMapper, update);
            item.setStatus("CANCELLED");
        }
        recalculateOrder(orderId, order.getDiscountAmount());
        return items;
    }

    public int getMaxBatchSeq(Long orderId) {
        List<OrderItemEntity> items = MapperUtils.selectList(orderItemMapper, OrderItemEntity.class,
                wrapper -> wrapper.eq("order_id", orderId));
        return items.stream()
                .mapToInt(item -> item.getBatchSeq() == null ? 0 : item.getBatchSeq())
                .max()
                .orElse(0);
    }

    public void removeItem(Long orderId, Long itemId) {
        OrderEntity order = requireEditableOrder(orderId);
        OrderItemEntity existing = getOrderItem(itemId);
        requireItemBelongsToOrder(existing, orderId);
        OrderItemEntity update = new OrderItemEntity();
        update.setId(itemId);
        update.setStatus("CANCELLED");
        MapperUtils.updateById(orderItemMapper, update);
        recalculateOrder(orderId, order.getDiscountAmount());
    }

    public OrderItemEntity updateItemQuantity(Long orderId, Long itemId, Integer quantity) {
        OrderEntity order = requireEditableOrder(orderId);
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Quantity must not be negative");
        }
        if (quantity == 0) {
            removeItem(orderId, itemId);
            return getOrderItem(itemId);
        }
        OrderItemEntity existing = getOrderItem(itemId);
        requireItemBelongsToOrder(existing, orderId);
        OrderItemEntity update = new OrderItemEntity();
        update.setId(itemId);
        update.setQuantity(quantity);
        update.setSubtotal(existing.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        MapperUtils.updateById(orderItemMapper, update);
        recalculateOrder(orderId, order.getDiscountAmount());
        existing.setQuantity(quantity);
        existing.setSubtotal(update.getSubtotal());
        return existing;
    }

    @Transactional
    public boolean confirmOrder(Long orderId) {
        OrderEntity order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        List<OrderItemEntity> items = getActiveOrderItems(orderId);
        if ("CONFIRMED".equals(order.getStatus())) {
            kitchenQueueService.enqueue(order, items);
            return true;
        }
        if (!"DRAFT".equals(order.getStatus())) {
            throw new IllegalStateException("Order can no longer be confirmed");
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot confirm an empty order");
        }
        OrderEntity update = new OrderEntity();
        update.setId(orderId);
        update.setStatus("CONFIRMED");
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(orderMapper, update);
        for (OrderItemEntity item : items) {
            OrderItemEntity updateItem = new OrderItemEntity();
            updateItem.setId(item.getId());
            updateItem.setBatchSeq(1);
            MapperUtils.updateById(orderItemMapper, updateItem);
        }
        kitchenQueueService.enqueue(order, items);
        return true;
    }

    public Map<String, Object> settleOrder(Long orderId, String paymentMethod, BigDecimal finalAmount, BigDecimal receivedAmount) {
        OrderEntity update = new OrderEntity();
        update.setId(orderId);
        update.setStatus("SETTLED");
        update.setPaymentMethod(paymentMethod);
        update.setFinalAmount(finalAmount);
        update.setSettledAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(orderMapper, update);

        BigDecimal change = BigDecimal.ZERO;
        if (receivedAmount != null && finalAmount != null) {
            change = receivedAmount.subtract(finalAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("finalAmount", finalAmount);
        result.put("changeAmount", change);
        return result;
    }

    public OrderEntity getOrder(Long id) {
        return MapperUtils.getById(orderMapper, id, "Order not found: " + id);
    }

    public OrderEntity findActiveOrderBySessionId(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        List<OrderEntity> orders = MapperUtils.selectList(orderMapper, OrderEntity.class,
                wrapper -> wrapper.eq("session_id", sessionId)
                        .notIn("status", List.of("SETTLED", "CANCELLED"))
                        .orderByDesc("created_at"));
        return orders.isEmpty() ? null : orders.getFirst();
    }

    public void updateTableAssociation(Long orderId, Long tableId) {
        OrderEntity update = new OrderEntity();
        update.setId(orderId);
        update.setTableId(tableId);
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(orderMapper, update);
    }

    public void updatePartySize(Long orderId, Integer partySize) {
        if (partySize == null || partySize < 1) {
            throw new IllegalArgumentException("Party size must be at least one");
        }
        OrderEntity update = new OrderEntity();
        update.setId(orderId);
        update.setPartySize(partySize);
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(orderMapper, update);
    }

    public List<OrderEntity> listOrders(String status, Long tableId, LocalDateTime dateFrom, LocalDateTime dateTo) {
        return MapperUtils.selectList(orderMapper, OrderEntity.class, wrapper -> {
            if (status != null && !status.isBlank()) {
                wrapper.eq("status", status);
            }
            if (tableId != null) {
                wrapper.eq("table_id", tableId);
            }
            if (dateFrom != null) {
                wrapper.ge("created_at", dateFrom);
            }
            if (dateTo != null) {
                wrapper.le("created_at", dateTo);
            }
            wrapper.orderByDesc("created_at");
        });
    }

    public List<OrderItemEntity> getOrderItems(Long orderId) {
        return MapperUtils.selectList(orderItemMapper, OrderItemEntity.class,
                wrapper -> wrapper.eq("order_id", orderId).orderByAsc("id"));
    }

    public List<OrderItemEntity> getActiveOrderItems(Long orderId) {
        return MapperUtils.selectList(orderItemMapper, OrderItemEntity.class,
                wrapper -> wrapper.eq("order_id", orderId)
                        .eq("status", "NORMAL")
                        .orderByAsc("id"));
    }

    public List<OrderItemEntity> getOrderItemsByBatch(Long orderId, Integer batchSeq) {
        return MapperUtils.selectList(orderItemMapper, OrderItemEntity.class,
                wrapper -> wrapper.eq("order_id", orderId)
                        .eq("batch_seq", batchSeq)
                        .orderByAsc("id"));
    }

    public List<OrderItemEntity> getActiveOrderItemsByBatch(Long orderId, Integer batchSeq) {
        return MapperUtils.selectList(orderItemMapper, OrderItemEntity.class,
                wrapper -> wrapper.eq("order_id", orderId)
                        .eq("batch_seq", batchSeq)
                        .eq("status", "NORMAL")
                        .orderByAsc("id"));
    }

    public OrderItemEntity getOrderItem(Long itemId) {
        return MapperUtils.getById(orderItemMapper, itemId, "Order item not found: " + itemId);
    }

    private void recalculateOrder(Long orderId, BigDecimal discountAmount) {
        List<OrderItemEntity> items = MapperUtils.selectList(orderItemMapper, OrderItemEntity.class,
                wrapper -> wrapper.eq("order_id", orderId).eq("status", "NORMAL"));
        BigDecimal total = items.stream()
                .map(OrderItemEntity::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        OrderEntity update = new OrderEntity();
        update.setId(orderId);
        update.setTotalAmount(total);
        update.setFinalAmount(total.subtract(discount).max(BigDecimal.ZERO));
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(orderMapper, update);
    }

    private OrderEntity requireEditableOrder(Long orderId) {
        OrderEntity order = getOrder(orderId);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new IllegalStateException("Order can no longer be edited");
        }
        return order;
    }

    private OrderEntity requireOrderAcceptsNewItems(Long orderId) {
        OrderEntity order = getOrder(orderId);
        if (!"DRAFT".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException("Order can no longer be edited");
        }
        return order;
    }

    private OrderEntity requireOrderAcceptsNewItemsForUpdate(Long orderId) {
        OrderEntity order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        if (!"DRAFT".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException("Order can no longer be edited");
        }
        return order;
    }

    private void requireItemBelongsToOrder(OrderItemEntity item, Long orderId) {
        if (!orderId.equals(item.getOrderId())) {
            throw new IllegalArgumentException("Order item does not belong to the order");
        }
    }

}
