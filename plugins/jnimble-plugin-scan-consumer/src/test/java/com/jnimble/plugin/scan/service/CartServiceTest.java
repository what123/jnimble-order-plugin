package com.jnimble.plugin.scan.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecGroupEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecOptionEntity;
import com.jnimble.plugin.menu.service.MenuItemService;
import com.jnimble.plugin.scan.mapper.CartItemMapper;
import com.jnimble.plugin.scan.mapper.CartMapper;
import com.jnimble.plugin.scan.model.entity.CartEntity;
import com.jnimble.plugin.scan.model.entity.CartItemEntity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartMapper cartMapper;

    @Mock
    private CartItemMapper cartItemMapper;

    @Mock
    private MenuItemService menuItemService;

    @Mock
    private ConsumerService consumerService;

    @InjectMocks
    private CartService cartService;

    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        mapperUtilsMock = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    @Test
    void addsSpecItemWithAdjustedPriceAndTagNames() {
        CartEntity cart = new CartEntity();
        cart.setId(100L);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(cartMapper), eq(CartEntity.class), any()))
                .thenReturn(List.of(cart));
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(cartItemMapper), eq(CartItemEntity.class), any()))
                .thenReturn(List.of());
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(cartItemMapper), any(CartItemEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        MenuItemEntity item = new MenuItemEntity();
        item.setId(10L);
        item.setName("宫保鸡丁");
        item.setPrice(new BigDecimal("28.00"));
        MenuItemSpecGroupEntity group = new MenuItemSpecGroupEntity();
        group.setId(1L);
        group.setName("辣度");
        group.setRequired(true);
        group.setMulti(false);
        MenuItemSpecOptionEntity option = new MenuItemSpecOptionEntity();
        option.setId(11L);
        option.setName("微辣");
        option.setPriceAdjust(new BigDecimal("1.00"));
        option.setStatus("ENABLED");
        group.setOptions(List.of(option));
        item.setGroups(List.of(group));
        when(menuItemService.getItem(10L)).thenReturn(item);

        cartService.updateCartItemQuantity(1L, 2L, "token", 10L, 2, " 11 ", 1);

        ArgumentCaptor<CartItemEntity> captor = ArgumentCaptor.forClass(CartItemEntity.class);
        mapperUtilsMock.verify(() -> MapperUtils.insert(eq(cartItemMapper), captor.capture()));
        CartItemEntity inserted = captor.getValue();
        assertEquals(new BigDecimal("29.00"), inserted.getUnitPrice());
        assertEquals(new BigDecimal("58.00"), inserted.getTotalPrice());
        assertEquals("11", inserted.getTagIds());
        assertEquals("辣度: 微辣", inserted.getTagNames());
    }

    @Test
    void rejectsAddWhenRequiredSpecMissing() {
        CartEntity cart = new CartEntity();
        cart.setId(100L);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(cartMapper), eq(CartEntity.class), any()))
                .thenReturn(List.of(cart));

        MenuItemEntity item = new MenuItemEntity();
        item.setId(10L);
        item.setName("宫保鸡丁");
        item.setPrice(new BigDecimal("28.00"));
        MenuItemSpecGroupEntity group = new MenuItemSpecGroupEntity();
        group.setId(1L);
        group.setName("辣度");
        group.setRequired(true);
        group.setMulti(false);
        item.setGroups(List.of(group));
        when(menuItemService.getItem(10L)).thenReturn(item);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> cartService.updateCartItemQuantity(1L, 2L, "token", 10L, 1, null, 1));
        assertEquals("请选择辣度", ex.getMessage());
    }

    @Test
    void removesItemWhenCountNotPositive() {
        CartEntity cart = new CartEntity();
        cart.setId(100L);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(cartMapper), eq(CartEntity.class), any()))
                .thenReturn(List.of(cart));
        CartItemEntity existing = new CartItemEntity();
        existing.setId(500L);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(cartItemMapper), eq(CartItemEntity.class), any()))
                .thenReturn(List.of(existing));

        MenuItemEntity item = new MenuItemEntity();
        item.setId(10L);
        item.setName("宫保鸡丁");
        item.setPrice(new BigDecimal("28.00"));
        when(menuItemService.getItem(10L)).thenReturn(item);

        CartItemEntity result = cartService.updateCartItemQuantity(1L, 2L, "token", 10L, 0, null, 1);

        assertNull(result);
        mapperUtilsMock.verify(() -> MapperUtils.deleteById(cartItemMapper, 500L));
    }
}
