package com.jnimble.plugin.scan.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemImageEntity;
import com.jnimble.plugin.menu.service.MenuItemService;
import com.jnimble.plugin.scan.mapper.CartItemMapper;
import com.jnimble.plugin.scan.mapper.CartMapper;
import com.jnimble.plugin.scan.model.entity.CartEntity;
import com.jnimble.plugin.scan.model.entity.CartItemEntity;
import com.jnimble.plugin.scan.model.entity.ConsumerEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;
    private final MenuItemService menuItemService;
    private final ConsumerService consumerService;

    public CartService(CartMapper cartMapper, CartItemMapper cartItemMapper,
                       MenuItemService menuItemService, ConsumerService consumerService) {
        this.cartMapper = cartMapper;
        this.cartItemMapper = cartItemMapper;
        this.menuItemService = menuItemService;
        this.consumerService = consumerService;
    }

    public CartEntity getOrCreateCart(Long storeId, Long tableId, String accessToken) {
        ConsumerEntity consumer = consumerService.getConsumerByAccessToken(accessToken);
        Long consumerId = consumer != null ? consumer.getId() : null;

        List<CartEntity> carts = MapperUtils.selectList(cartMapper, CartEntity.class,
                wrapper -> {
                    wrapper.eq("status", "ACTIVE");
                    if (consumerId != null) {
                        wrapper.eq("consumer_id", consumerId);
                    } else {
                        wrapper.isNull("consumer_id");
                    }
                    if (storeId != null) {
                        wrapper.eq("store_id", storeId);
                    }
                    if (tableId != null) {
                        wrapper.eq("table_id", tableId);
                    } else {
                        wrapper.isNull("table_id");
                    }
                    wrapper.orderByDesc("created_at").last("LIMIT 1");
                });

        if (!carts.isEmpty()) {
            return carts.getFirst();
        }

        CartEntity cart = new CartEntity();
        cart.setConsumerId(consumerId);
        cart.setStoreId(storeId);
        cart.setTableId(tableId);
        cart.setStatus("ACTIVE");
        cart.setCreatedAt(LocalDateTime.now());
        cart.setUpdatedAt(LocalDateTime.now());
        return MapperUtils.insert(cartMapper, cart);
    }

    public List<CartItemEntity> getCartItems(Long cartId) {
        return MapperUtils.selectList(cartItemMapper, CartItemEntity.class,
                wrapper -> wrapper.eq("cart_id", cartId).orderByAsc("id"));
    }

    @Transactional
    public CartItemEntity updateCartItemQuantity(Long storeId, Long tableId, String accessToken,
                                                  Long goodsId, Integer count,
                                                  String tagIds, Integer orderType) {
        CartEntity cart = getOrCreateCart(storeId, tableId, accessToken);
        MenuItemEntity menuItem = menuItemService.getItem(goodsId);
        BigDecimal unitPrice = menuItem.getPrice();
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(count));

        List<CartItemEntity> existingItems = MapperUtils.selectList(cartItemMapper, CartItemEntity.class,
                wrapper -> {
                    wrapper.eq("cart_id", cart.getId()).eq("menu_item_id", goodsId);
                    if (tagIds != null && !tagIds.isBlank()) {
                        wrapper.eq("tag_ids", tagIds);
                    } else {
                        wrapper.and(w -> w.isNull("tag_ids").or().eq("tag_ids", ""));
                    }
                });

        if (count <= 0) {
            for (CartItemEntity item : existingItems) {
                MapperUtils.deleteById(cartItemMapper, item.getId());
            }
            return null;
        }

        if (!existingItems.isEmpty()) {
            CartItemEntity item = existingItems.getFirst();
            item.setQuantity(count);
            item.setTotalPrice(totalPrice);
            item.setUpdatedAt(LocalDateTime.now());
            return MapperUtils.updateById(cartItemMapper, item);
        }

        CartItemEntity newItem = new CartItemEntity();
        newItem.setCartId(cart.getId());
        newItem.setMenuItemId(goodsId);
        newItem.setItemName(menuItem.getName());
        newItem.setUnitPrice(unitPrice);
        newItem.setQuantity(count);
        newItem.setTotalPrice(totalPrice);
        newItem.setTagIds(tagIds);
        newItem.setCreatedAt(LocalDateTime.now());
        newItem.setUpdatedAt(LocalDateTime.now());
        return MapperUtils.insert(cartItemMapper, newItem);
    }

    @Transactional
    public void clearCart(Long cartId) {
        MapperUtils.deleteByCondition(cartItemMapper, CartItemEntity.class,
                wrapper -> wrapper.eq("cart_id", cartId));
        CartEntity update = new CartEntity();
        update.setId(cartId);
        update.setStatus("ORDERED");
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(cartMapper, update);
    }

    @Transactional
    public void clearCartItems(Long cartId) {
        MapperUtils.deleteByCondition(cartItemMapper, CartItemEntity.class,
                wrapper -> wrapper.eq("cart_id", cartId));
    }

    public String formatTotalPrice(List<CartItemEntity> items) {
        BigDecimal total = items.stream()
                .map(CartItemEntity::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String formatTotalPrice2(List<CartItemEntity> items) {
        return formatTotalPrice(items);
    }
}
