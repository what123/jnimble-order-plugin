package com.jnimble.plugin.order.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.mapper.OrderItemMapper;
import com.jnimble.plugin.order.mapper.OrderMapper;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 订单服务单元测试。
 * 测试订单创建、添加商品、移除商品、确认订单、结算订单等核心功能。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private KitchenQueueService kitchenQueueService;

    @InjectMocks
    private OrderService orderService;

    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        mapperUtilsMock = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    /**
     * 测试创建订单：验证订单号生成、状态设置以及点餐来源关联字段。
     */
    @Test
    void testCreateOrder() {
        // 准备测试数据
        Long tableId = 1L;
        Integer partySize = 4;
        String operator = "testUser";
        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(orderMapper), eq(OrderEntity.class), any()))
                .thenReturn(Collections.emptyList());
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(orderMapper), any(OrderEntity.class)))
                .thenAnswer(invocation -> {
                    OrderEntity entity = invocation.getArgument(1);
                    entity.setId(1L);
                    return entity;
                });
        // 执行测试
        OrderEntity result = orderService.createOrder(tableId, partySize, operator);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getOrderNo());
        assertTrue(result.getOrderNo().startsWith("D"));
        assertEquals("DRAFT", result.getStatus());
        assertEquals(BigDecimal.ZERO, result.getTotalAmount());
        assertEquals(tableId, result.getTableId());
        assertEquals(partySize, result.getPartySize());
        assertEquals(operator, result.getOperator());
        assertEquals("LEGACY", result.getSource());
        assertNotNull(result.getBusinessDate());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.insert(eq(orderMapper), any(OrderEntity.class)));
    }

    /**
     * 测试添加订单项：验证商品添加、小计计算以及订单总额重新计算。
     */
    @Test
    void testAddItem() {
        // 准备测试数据
        Long orderId = 1L;
        Long menuItemId = 100L;
        String itemName = "测试商品";
        BigDecimal unitPrice = new BigDecimal("25.50");
        Integer quantity = 2;
        String remark = "少辣";
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("DRAFT");

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.getById(eq(orderMapper), eq(orderId), any()))
                .thenReturn(order);
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(orderItemMapper), any(OrderItemEntity.class)))
                .thenAnswer(invocation -> {
                    OrderItemEntity item = invocation.getArgument(1);
                    item.setId(10L);
                    return item;
                });
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(orderItemMapper), eq(OrderItemEntity.class), any()))
                .thenReturn(Collections.emptyList());
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)))
                .thenReturn(null);

        // 执行测试
        OrderItemEntity result = orderService.addItem(orderId, menuItemId, itemName, unitPrice, quantity, remark);

        // 验证结果
        assertNotNull(result);
        assertEquals(orderId, result.getOrderId());
        assertEquals(menuItemId, result.getMenuItemId());
        assertEquals(itemName, result.getItemName());
        assertEquals(unitPrice, result.getUnitPrice());
        assertEquals(quantity, result.getQuantity());
        assertEquals(unitPrice.multiply(BigDecimal.valueOf(quantity)), result.getSubtotal());
        assertEquals(remark, result.getRemark());
        assertEquals("NORMAL", result.getStatus());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.insert(eq(orderItemMapper), any(OrderItemEntity.class)));
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)));
        verify(kitchenQueueService, never()).enqueue(any(), any());
    }

    @Test
    void addItemToConfirmedOrderDispatchesOnlyTheNewItem() {
        Long orderId = 1L;
        Long menuItemId = 100L;
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("CONFIRMED");
        order.setOrderNo("D202607140001");
        order.setTableId(2L);
        order.setSessionId(21L);
        order.setSource("TABLE");

        mapperUtilsMock.when(() -> MapperUtils.getById(eq(orderMapper), eq(orderId), any()))
                .thenReturn(order);
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(orderItemMapper), any(OrderItemEntity.class)))
                .thenAnswer(invocation -> {
                    OrderItemEntity item = invocation.getArgument(1);
                    item.setId(11L);
                    return item;
                });
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(orderItemMapper), eq(OrderItemEntity.class), any()))
                .thenReturn(Collections.emptyList());
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)))
                .thenReturn(null);

        OrderItemEntity result = orderService.addItem(
                orderId, menuItemId, "加菜商品", new BigDecimal("12.00"), 1, null
        );

        assertEquals(11L, result.getId());
        verify(kitchenQueueService).enqueue(order, List.of(result));
    }

    @Test
    void stageBatchItemsDoesNotDispatchConfirmedOrder() {
        Long orderId = 1L;
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("CONFIRMED");
        OrderItemEntity previous = new OrderItemEntity();
        previous.setBatchSeq(1);
        OrderItemEntity addOn = new OrderItemEntity();
        addOn.setUnitPrice(new BigDecimal("18.00"));
        addOn.setQuantity(2);

        when(orderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(orderItemMapper), eq(OrderItemEntity.class), any()))
                .thenReturn(List.of(previous), List.of(addOn));
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(orderItemMapper), any(OrderItemEntity.class)))
                .thenAnswer(invocation -> {
                    OrderItemEntity item = invocation.getArgument(1);
                    item.setId(12L);
                    return item;
                });
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)))
                .thenReturn(null);

        List<OrderItemEntity> created = orderService.stageBatchItems(orderId, List.of(addOn));

        assertEquals(2, created.getFirst().getBatchSeq());
        assertEquals(new BigDecimal("36.00"), created.getFirst().getSubtotal());
        verify(kitchenQueueService, never()).enqueue(any(), any());
    }

    @Test
    void confirmBatchDispatchesOnlySelectedBatch() {
        Long orderId = 1L;
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("CONFIRMED");
        OrderItemEntity addOn = new OrderItemEntity();
        addOn.setId(12L);
        addOn.setOrderId(orderId);
        addOn.setBatchSeq(2);
        when(orderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(orderItemMapper), eq(OrderItemEntity.class), any()))
                .thenReturn(List.of(addOn));

        List<OrderItemEntity> result = orderService.confirmBatch(orderId, 2);

        assertEquals(List.of(addOn), result);
        verify(kitchenQueueService).enqueue(order, List.of(addOn));
    }

    @Test
    void cancelAddOnBatchExcludesItemsFromOrderTotal() {
        Long orderId = 1L;
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("CONFIRMED");
        order.setDiscountAmount(new BigDecimal("5.00"));
        OrderItemEntity addOn = new OrderItemEntity();
        addOn.setId(12L);
        addOn.setOrderId(orderId);
        addOn.setBatchSeq(2);
        addOn.setStatus("NORMAL");
        OrderItemEntity remaining = new OrderItemEntity();
        remaining.setSubtotal(new BigDecimal("20.00"));
        AtomicReference<OrderEntity> recalculation = new AtomicReference<>();
        when(orderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(orderItemMapper), eq(OrderItemEntity.class), any()))
                .thenReturn(List.of(addOn), List.of(remaining));
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderItemMapper), any(OrderItemEntity.class)))
                .thenReturn(null);
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)))
                .thenAnswer(invocation -> {
                    recalculation.set(invocation.getArgument(1));
                    return null;
                });

        orderService.cancelAddOnBatch(orderId, 2);

        assertEquals("CANCELLED", addOn.getStatus());
        assertEquals(new BigDecimal("20.00"), recalculation.get().getTotalAmount());
        assertEquals(new BigDecimal("15.00"), recalculation.get().getFinalAmount());
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)));
        verify(kitchenQueueService, never()).enqueue(any(), any());
    }

    /**
     * 测试移除订单项：验证商品状态更新为已取消以及订单总额重新计算。
     */
    @Test
    void testRemoveItem() {
        // 准备测试数据
        Long orderId = 1L;
        Long itemId = 10L;
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("DRAFT");
        OrderItemEntity existing = new OrderItemEntity();
        existing.setId(itemId);
        existing.setOrderId(orderId);

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.getById(eq(orderMapper), eq(orderId), any()))
                .thenReturn(order);
        mapperUtilsMock.when(() -> MapperUtils.getById(eq(orderItemMapper), eq(itemId), any()))
                .thenReturn(existing);
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderItemMapper), any(OrderItemEntity.class)))
                .thenReturn(null);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(orderItemMapper), eq(OrderItemEntity.class), any()))
                .thenReturn(Collections.emptyList());
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)))
                .thenReturn(null);

        // 执行测试
        orderService.removeItem(orderId, itemId);

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(orderItemMapper), any(OrderItemEntity.class)));
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)));
    }

    /**
     * 测试确认订单：验证订单状态更新为已确认。
     */
    @Test
    void testConfirmOrder() {
        // 准备测试数据
        Long orderId = 1L;
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("DRAFT");
        OrderItemEntity item = new OrderItemEntity();
        item.setId(10L);
        item.setOrderId(orderId);

        // 模拟MapperUtils行为
        when(orderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(orderItemMapper), eq(OrderItemEntity.class), any()))
                .thenReturn(List.of(item));
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)))
                .thenReturn(null);
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderItemMapper), any(OrderItemEntity.class)))
                .thenReturn(null);

        // 执行测试
        boolean result = orderService.confirmOrder(orderId);

        // 验证结果
        assertTrue(result);

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)));
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(orderItemMapper), any(OrderItemEntity.class)));
        verify(kitchenQueueService).enqueue(order, List.of(item));
    }

    @Test
    void confirmingAnAlreadyConfirmedOrderIsIdempotent() {
        Long orderId = 1L;
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setStatus("CONFIRMED");
        OrderItemEntity item = new OrderItemEntity();
        item.setId(10L);
        item.setOrderId(orderId);
        when(orderMapper.selectByIdForUpdate(orderId)).thenReturn(order);
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(orderItemMapper), eq(OrderItemEntity.class), any()))
                .thenReturn(List.of(item));

        assertTrue(orderService.confirmOrder(orderId));

        verify(kitchenQueueService).enqueue(order, List.of(item));
    }

    /**
     * 测试结算订单：验证订单状态更新以及找零计算。
     */
    @Test
    void testSettleOrder() {
        // 准备测试数据
        Long orderId = 1L;
        String paymentMethod = "CASH";
        BigDecimal finalAmount = new BigDecimal("100.00");
        BigDecimal receivedAmount = new BigDecimal("150.00");

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)))
                .thenReturn(null);

        // 执行测试
        Map<String, Object> result = orderService.settleOrder(orderId, paymentMethod, finalAmount, receivedAmount);

        // 验证结果
        assertNotNull(result);
        assertEquals(orderId, result.get("orderId"));
        assertEquals(finalAmount, result.get("finalAmount"));
        assertEquals(new BigDecimal("50.00"), result.get("changeAmount"));

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(orderMapper), any(OrderEntity.class)));
    }
}
