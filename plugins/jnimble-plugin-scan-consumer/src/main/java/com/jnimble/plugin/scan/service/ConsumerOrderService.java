package com.jnimble.plugin.scan.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.service.MenuItemService;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.service.OrderService;
import com.jnimble.plugin.scan.mapper.CartItemMapper;
import com.jnimble.plugin.scan.mapper.CartMapper;
import com.jnimble.plugin.scan.model.entity.CartEntity;
import com.jnimble.plugin.scan.model.entity.CartItemEntity;
import com.jnimble.plugin.scan.model.entity.ScanTableEntity;
import com.jnimble.plugin.scan.model.entity.StoreEntity;
import com.jnimble.plugin.scan.model.entity.ConsumerEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerOrderService {

    private final OrderService orderService;
    private final CartService cartService;
    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;
    private final MenuItemService menuItemService;
    private final ConsumerService consumerService;

    public ConsumerOrderService(OrderService orderService,
                                CartService cartService,
                                CartMapper cartMapper,
                                CartItemMapper cartItemMapper,
                                MenuItemService menuItemService,
                                ConsumerService consumerService) {
        this.orderService = orderService;
        this.cartService = cartService;
        this.cartMapper = cartMapper;
        this.cartItemMapper = cartItemMapper;
        this.menuItemService = menuItemService;
        this.consumerService = consumerService;
    }

    @Transactional
    public Map<String, Object> createOrder(Long storeId, Long tableId, String accessToken,
                                            Integer orderType, Integer peopleCount,
                                            String orderStartTime, String note) {
        CartEntity cart = cartService.getOrCreateCart(storeId, tableId, accessToken);
        List<CartItemEntity> cartItems = cartService.getCartItems(cart.getId());

        if (cartItems.isEmpty()) {
            throw new IllegalStateException("购物车为空");
        }

        boolean hasUnpaid = hasOrderedCart(storeId, tableId, accessToken);
        Long orderId;

        if (hasUnpaid) {
            CartEntity unpaidCart = getUnpaidCart(storeId, tableId, accessToken);
            orderId = unpaidCart.getOrderId();
            OrderEntity existingOrder = orderService.getOrder(orderId);
            int nextBatch = getNextBatchSeq(orderId);

            for (CartItemEntity cartItem : cartItems) {
                orderService.addItem(orderId, cartItem.getMenuItemId(),
                        cartItem.getItemName(), cartItem.getTagNames(),
                        cartItem.getUnitPrice(), cartItem.getQuantity(),
                        cartItem.getRemark(), nextBatch);
            }

            cart.setStatus("ORDERED");
            cart.setOrderId(orderId);
            cart.setUpdatedAt(LocalDateTime.now());
            MapperUtils.updateById(cartMapper, cart);
        } else {
            orderId = orderService.createOrder(tableId, peopleCount, "consumer", null, "SCAN").getId();

            for (CartItemEntity cartItem : cartItems) {
                orderService.addItem(orderId, cartItem.getMenuItemId(),
                        cartItem.getItemName(), cartItem.getTagNames(),
                        cartItem.getUnitPrice(), cartItem.getQuantity(),
                        cartItem.getRemark(), 0);
            }

            List<MenuItemEntity> forcedItems = menuItemService.getForceSelectedItems(storeId);
            for (MenuItemEntity item : forcedItems) {
                boolean alreadyInCart = cartItems.stream()
                        .anyMatch(ci -> ci.getMenuItemId().equals(item.getId()));
                if (!alreadyInCart) {
                    orderService.addItem(orderId, item.getId(),
                            item.getName(), null,
                            item.getPrice(), 1, null, 0);
                }
            }

            orderService.confirmOrder(orderId);

            cart.setStatus("ORDERED");
            cart.setOrderId(orderId);
            cart.setUpdatedAt(LocalDateTime.now());
            MapperUtils.updateById(cartMapper, cart);
        }

        cartService.clearCartItems(cart.getId());

        Map<String, Object> orderResult = new HashMap<>();
        orderResult.put("id", String.valueOf(orderId));
        Map<String, Object> data = new HashMap<>();
        data.put("order", orderResult);
        return data;
    }

    private CartEntity getUnpaidCart(Long storeId, Long tableId, String accessToken) {
        ConsumerEntity consumer = consumerService.getConsumerByAccessToken(accessToken);
        Long consumerId = consumer != null ? consumer.getId() : null;

        List<CartEntity> carts = MapperUtils.selectList(cartMapper, CartEntity.class,
                wrapper -> {
                    wrapper.eq("status", "ORDERED");
                    if (consumerId != null) {
                        wrapper.eq("consumer_id", consumerId);
                    }
                    if (storeId != null) {
                        wrapper.eq("store_id", storeId);
                    }
                    if (tableId != null) {
                        wrapper.eq("table_id", tableId);
                    }
                    wrapper.orderByDesc("updated_at").last("LIMIT 1");
                });

        if (carts.isEmpty() || carts.getFirst().getOrderId() == null) {
            throw new IllegalArgumentException("No unpaid order");
        }

        CartEntity cart = carts.getFirst();
        OrderEntity order = orderService.getOrder(cart.getOrderId());
        if ("SETTLED".equals(order.getStatus())) {
            throw new IllegalArgumentException("No unpaid order");
        }
        return cart;
    }

    private int getNextBatchSeq(Long orderId) {
        List<OrderItemEntity> items = orderService.getOrderItems(orderId);
        int maxBatch = 0;
        for (OrderItemEntity item : items) {
            int batch = item.getBatchSeq() == null ? 0 : item.getBatchSeq();
            if (batch > maxBatch) maxBatch = batch;
        }
        return maxBatch + 1;
    }

    private boolean hasOrderedCart(Long storeId, Long tableId, String accessToken) {
        try {
            getUnpaidOrder(storeId, tableId, accessToken);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> getUnpaidOrder(Long storeId, Long tableId, String accessToken) {
        ConsumerEntity consumer = consumerService.getConsumerByAccessToken(accessToken);
        Long consumerId = consumer != null ? consumer.getId() : null;

        List<CartEntity> carts = MapperUtils.selectList(cartMapper, CartEntity.class,
                wrapper -> {
                    wrapper.eq("status", "ORDERED");
                    if (consumerId != null) {
                        wrapper.eq("consumer_id", consumerId);
                    }
                    if (storeId != null) {
                        wrapper.eq("store_id", storeId);
                    }
                    wrapper.orderByDesc("updated_at").last("LIMIT 1");
                });

        if (carts.isEmpty() || carts.getFirst().getOrderId() == null) {
            throw new IllegalArgumentException("No unpaid order");
        }

        CartEntity cart = carts.getFirst();
        OrderEntity order = orderService.getOrder(cart.getOrderId());

        if ("SETTLED".equals(order.getStatus())) {
            throw new IllegalArgumentException("No unpaid order");
        }
        List<OrderItemEntity> items = orderService.getActiveOrderItems(order.getId());

        Map<String, Object> orderSummary = new HashMap<>();
        orderSummary.put("id", String.valueOf(order.getId()));
        orderSummary.put("totalPrice", order.getTotalAmount());

        List<Map<String, Object>> groups = new ArrayList<>();
        Map<Integer, List<OrderItemEntity>> batchMap = new HashMap<>();
        for (OrderItemEntity item : items) {
            int batch = item.getBatchSeq() == null ? 0 : item.getBatchSeq();
            batchMap.computeIfAbsent(batch, k -> new ArrayList<>()).add(item);
        }
        int index = 1;
        for (Map.Entry<Integer, List<OrderItemEntity>> entry : batchMap.entrySet()) {
            Map<String, Object> group = new HashMap<>();
            group.put("indexGroup", index++);
            List<Map<String, Object>> goodsList = new ArrayList<>();
            for (OrderItemEntity item : entry.getValue()) {
                Map<String, Object> goodsItem = new HashMap<>();
                Map<String, Object> goods = new HashMap<>();
                goods.put("id", String.valueOf(item.getMenuItemId()));
                goods.put("name", item.getItemName());
                goods.put("pics", List.of());
                goods.put("formatSellPrice", item.getUnitPrice().toPlainString());
                goods.put("unit", "");
                goodsItem.put("goods", goods);
                goodsItem.put("count", item.getQuantity());
                goodsItem.put("goodsName", item.getItemName());
                goodsItem.put("goodsTagItemNames", item.getSpecification() == null ? "" : item.getSpecification());
                goodsList.add(goodsItem);
            }
            group.put("orderGoodsList", goodsList);
            groups.add(group);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("order", orderSummary);
        data.put("orderGoodsIndexGroups", groups);
        return data;
    }

    public Map<String, Object> getOrderDetail(Long orderId, String accessToken) {
        OrderEntity order = orderService.getOrder(orderId);

        ConsumerEntity consumer = consumerService.getConsumerByAccessToken(accessToken);
        boolean isLoggedIn = consumer != null;
        if (!isLoggedIn && "SETTLED".equals(order.getStatus())) {
            throw new RuntimeException("订单已结账，请登录后查看");
        }

        List<OrderItemEntity> items = orderService.getOrderItems(orderId);

        Map<String, Object> orderDetail = new HashMap<>();
        orderDetail.put("id", String.valueOf(order.getId()));
        orderDetail.put("payStatus", "SETTLED".equals(order.getStatus()) ? 2 : 1);
        orderDetail.put("type", 1);
        orderDetail.put("getOrderNumber", order.getOrderNo());
        orderDetail.put("orderNo", order.getOrderNo());
        orderDetail.put("formatTotalPrice", formatAmount(order.getTotalAmount()));
        orderDetail.put("formatDiscountPrice", formatAmount(order.getDiscountAmount()));
        orderDetail.put("formatFinalPrice", formatAmount(order.getFinalAmount()));
        orderDetail.put("statusStr", getStatusStr(order.getStatus()));
        orderDetail.put("createTimeStr", formatDateTime(order.getCreatedAt()));
        orderDetail.put("startTimeStr", formatDateTime(order.getCreatedAt()));
        orderDetail.put("note", order.getRemark() == null ? "" : order.getRemark());
        orderDetail.put("discountPrice", order.getDiscountAmount());

        List<Map<String, Object>> orderGoods = new ArrayList<>();
        Map<Integer, List<Map<String, Object>>> batchMap = new HashMap<>();
        int maxBatch = 0;
        for (OrderItemEntity item : items) {
            Map<String, Object> goodsItem = new HashMap<>();
            Map<String, Object> goods = new HashMap<>();
            goods.put("id", String.valueOf(item.getMenuItemId()));
            goods.put("name", item.getItemName());
            goods.put("pics", List.of());
            goods.put("formatSellPrice", item.getUnitPrice().toPlainString());
            goods.put("unit", "");
            goodsItem.put("goods", goods);
            goodsItem.put("count", item.getQuantity());
            goodsItem.put("goodsName", item.getItemName());
            goodsItem.put("goodsTagItemNames", item.getSpecification() == null ? "" : item.getSpecification());
            orderGoods.add(goodsItem);

            int batch = item.getBatchSeq() == null ? 0 : item.getBatchSeq();
            batchMap.computeIfAbsent(batch, k -> new ArrayList<>()).add(goodsItem);
            if (batch > maxBatch) maxBatch = batch;
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        int index = 1;
        for (Map.Entry<Integer, List<Map<String, Object>>> entry : batchMap.entrySet()) {
            Map<String, Object> group = new HashMap<>();
            group.put("indexGroup", index++);
            group.put("orderGoodsList", entry.getValue());
            groups.add(group);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("order", orderDetail);
        data.put("orderGoods", orderGoods);
        data.put("orderGoodsIndexGroups", groups);
        data.put("itemsVo", new HashMap<>());
        data.put("order_no_qr_code", "");
        return data;
    }

    public Map<String, Object> listOrders(String accessToken, Long storeId, Long tableId, int page, int pageSize) {
        ConsumerEntity consumer = consumerService.getConsumerByAccessToken(accessToken);
        Long consumerId = consumer != null ? consumer.getId() : null;
        boolean isLoggedIn = consumerId != null;

        List<CartEntity> orderedCarts = MapperUtils.selectList(cartMapper, CartEntity.class,
                wrapper -> {
                    wrapper.eq("status", "ORDERED");
                    if (storeId != null) {
                        wrapper.eq("store_id", storeId);
                    }
                    if (tableId != null) {
                        wrapper.eq("table_id", tableId);
                    }
                    wrapper.orderByDesc("updated_at");
                });

        if (isLoggedIn) {
            List<CartEntity> consumerCarts = MapperUtils.selectList(cartMapper, CartEntity.class,
                    wrapper -> {
                        wrapper.eq("consumer_id", consumerId);
                        wrapper.orderByDesc("updated_at");
                    });
            orderedCarts.addAll(consumerCarts);
            orderedCarts = orderedCarts.stream()
                    .distinct()
                    .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                    .collect(java.util.stream.Collectors.toList());
        }

        orderedCarts = orderedCarts.stream()
                .filter(c -> c.getOrderId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        CartEntity::getOrderId,
                        c -> c,
                        (a, b) -> a))
                .values()
                .stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .collect(java.util.stream.Collectors.toList());

        List<Map<String, Object>> content = new ArrayList<>();
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, orderedCarts.size());
        int totalPages = (int) Math.ceil((double) orderedCarts.size() / pageSize);
        if (totalPages == 0) totalPages = 1;

        for (int i = start; i < end; i++) {
            CartEntity cart = orderedCarts.get(i);
            if (cart.getOrderId() == null) continue;
            OrderEntity order = orderService.getOrder(cart.getOrderId());

            if (!isLoggedIn && "SETTLED".equals(order.getStatus())) {
                continue;
            }

            List<OrderItemEntity> items = orderService.getOrderItems(cart.getOrderId());

            Map<String, Object> orderItem = new HashMap<>();
            orderItem.put("id", String.valueOf(order.getId()));
            orderItem.put("orderNo", order.getOrderNo());
            orderItem.put("statusStr", getStatusStr(order.getStatus()));
            orderItem.put("formatFinalPrice", formatAmount(order.getFinalAmount()));

            List<Map<String, Object>> goodsList = new ArrayList<>();
            for (OrderItemEntity item : items) {
                Map<String, Object> goodsItem = new HashMap<>();
                Map<String, Object> goods = new HashMap<>();
                goods.put("id", String.valueOf(item.getMenuItemId()));
                goods.put("name", item.getItemName());
                goods.put("pics", List.of());
                goods.put("formatSellPrice", item.getUnitPrice().toPlainString());
                goods.put("unit", "");
                goodsItem.put("goods", goods);
                goodsItem.put("count", item.getQuantity());
                goodsList.add(goodsItem);
            }
            orderItem.put("orderGoodsList", goodsList);
            content.add(orderItem);
        }

        Map<String, Object> orders = new HashMap<>();
        orders.put("content", content);
        orders.put("totalPages", totalPages);

        Map<String, Object> data = new HashMap<>();
        data.put("orders", orders);
        return data;
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String getStatusStr(String status) {
        return switch (status) {
            case "DRAFT" -> "待确认";
            case "CONFIRMED" -> "已确认";
            case "SETTLED" -> "已结账";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }
}
