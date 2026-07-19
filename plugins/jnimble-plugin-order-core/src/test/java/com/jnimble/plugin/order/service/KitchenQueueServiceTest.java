package com.jnimble.plugin.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.kitchen.KitchenDispatchMode;
import com.jnimble.plugin.order.kitchen.KitchenDispatchModeRegistry;
import com.jnimble.plugin.order.kitchen.KitchenPrintGatewayRegistry;
import com.jnimble.plugin.order.kitchen.KitchenPrintResult;
import com.jnimble.plugin.order.kitchen.KitchenTicketNumberRegistry;
import com.jnimble.plugin.order.mapper.KitchenQueueItemMapper;
import com.jnimble.plugin.order.mapper.OrderMapper;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.QueueResponse;
import com.jnimble.plugin.order.model.entity.KitchenQueueItemEntity;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KitchenQueueServiceTest {

    @Mock
    private KitchenQueueItemMapper queueItemMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private KitchenDispatchModeRegistry dispatchModeRegistry;
    @Mock
    private KitchenTicketNumberRegistry ticketNumberRegistry;
    @Mock
    private KitchenPrintGatewayRegistry printGatewayRegistry;

    private KitchenQueueService service;
    private MockedStatic<MapperUtils> mapperUtils;

    @BeforeEach
    void setUp() {
        service = new KitchenQueueService(
                queueItemMapper,
                orderMapper,
                dispatchModeRegistry,
                ticketNumberRegistry,
                printGatewayRegistry
        );
        mapperUtils = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtils.close();
    }

    @Test
    void enqueueCreatesOneSnapshotPerNewOrderItem() {
        OrderEntity order = order(10L, "D001");
        OrderItemEntity orderItem = new OrderItemEntity();
        orderItem.setId(101L);
        orderItem.setMenuItemId(7L);
        orderItem.setItemName("宫保鸡丁");
        orderItem.setSpecification("微辣");
        orderItem.setRemark("少盐");
        orderItem.setQuantity(2);
        orderItem.setBatchSeq(3);
        AtomicReference<KitchenQueueItemEntity> inserted = new AtomicReference<>();

        mapperUtils.when(() -> MapperUtils.selectList(eq(queueItemMapper), eq(KitchenQueueItemEntity.class), any()))
                .thenReturn(List.of());
        mapperUtils.when(() -> MapperUtils.insert(eq(queueItemMapper), any(KitchenQueueItemEntity.class)))
                .thenAnswer(invocation -> {
                    KitchenQueueItemEntity entity = invocation.getArgument(1);
                    inserted.set(entity);
                    return entity;
                });
        when(dispatchModeRegistry.resolve(any())).thenReturn(KitchenDispatchMode.PAPERLESS);

        service.enqueue(order, List.of(orderItem));

        assertEquals(101L, inserted.get().getOrderItemId());
        assertEquals("宫保鸡丁", inserted.get().getItemName());
        assertEquals("微辣", inserted.get().getSpecification());
        assertEquals(3, inserted.get().getOrderBatchSeq());
        assertEquals("PAPERLESS", inserted.get().getDispatchMode());
        assertEquals("WAITING", inserted.get().getStatus());
    }

    @Test
    void groupedWaitingCombinesSameDishAndSpecificationOnly() {
        List<KitchenQueueItemEntity> items = List.of(
                queueItem(1L, 10L, 7L, "宫保鸡丁", "微辣", 1),
                queueItem(2L, 11L, 7L, "宫保鸡丁", "微辣", 2),
                queueItem(3L, 12L, 7L, "宫保鸡丁", "中辣", 1)
        );
        mockQueueList(items);
        when(ticketNumberRegistry.resolve(any())).thenAnswer(invocation ->
                "桌" + invocation.<com.jnimble.plugin.order.kitchen.KitchenTicketNumberContext>getArgument(0).orderId());

        QueueResponse response = service.list("WAITING", "PAPERLESS", true);

        assertTrue(response.grouped());
        assertEquals(2, response.groups().size());
        assertEquals(3, response.groups().get(0).totalQuantity());
        assertEquals(2, response.groups().get(0).orderCount());
        assertEquals(1, response.groups().get(1).totalQuantity());
    }

    @Test
    void groupingIsRejectedOutsidePaperlessWaiting() {
        assertThrows(IllegalArgumentException.class,
                () -> service.list("COOKING", "PAPERLESS", true));
        assertThrows(IllegalArgumentException.class,
                () -> service.list("WAITING", "PRINT", true));
    }

    @Test
    void completedQueueUsesFixedSizePagination() {
        KitchenQueueItemEntity item = queueItem(1L, 10L, 7L, "宫保鸡丁", "微辣", 1);
        item.setStatus("COMPLETED");
        mockQueueList(List.of(item));
        when(queueItemMapper.selectCount(any())).thenReturn(125L);
        when(ticketNumberRegistry.resolve(any())).thenReturn("桌10");

        QueueResponse response = service.list("COMPLETED", "PAPERLESS", false, 2);

        assertTrue(response.paged());
        assertEquals(2, response.page());
        assertEquals(50, response.pageSize());
        assertEquals(125, response.total());
        assertEquals(3, response.totalPages());
        assertEquals(1, response.items().size());
    }

    @Test
    void liveQueueIsNotPaged() {
        mockQueueList(List.of());

        QueueResponse response = service.list("WAITING", "PAPERLESS", false, 99);

        assertFalse(response.paged());
        assertEquals(1, response.page());
        assertEquals(0, response.total());
    }

    @Test
    void startGroupRejectsDifferentSpecifications() {
        KitchenQueueItemEntity first = queueItem(1L, 10L, 7L, "宫保鸡丁", "微辣", 1);
        KitchenQueueItemEntity second = queueItem(2L, 11L, 7L, "宫保鸡丁", "中辣", 1);
        mapperUtils.when(() -> MapperUtils.getById(eq(queueItemMapper), eq(1L), any())).thenReturn(first);
        mapperUtils.when(() -> MapperUtils.getById(eq(queueItemMapper), eq(2L), any())).thenReturn(second);

        assertThrows(IllegalArgumentException.class,
                () -> service.startGroup(List.of("1", "2")));

        verify(queueItemMapper, never()).start(any(), anyString(), any());
    }

    @Test
    void paperlessItemTransitionsFromWaitingToCooking() {
        KitchenQueueItemEntity item = queueItem(1L, 10L, 7L, "宫保鸡丁", "微辣", 1);
        mapperUtils.when(() -> MapperUtils.getById(eq(queueItemMapper), eq(1L), any())).thenReturn(item);
        when(queueItemMapper.start(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(1);

        service.start(1L);

        ArgumentCaptor<String> batchNo = ArgumentCaptor.forClass(String.class);
        verify(queueItemMapper).start(eq(1L), batchNo.capture(), any(LocalDateTime.class));
        assertFalse(batchNo.getValue().isBlank());
    }

    @Test
    void groupedItemsStartInTheSameProductionBatch() {
        KitchenQueueItemEntity first = queueItem(1L, 10L, 7L, "宫保鸡丁", "微辣", 1);
        KitchenQueueItemEntity second = queueItem(2L, 11L, 7L, "宫保鸡丁", "微辣", 2);
        mapperUtils.when(() -> MapperUtils.getById(eq(queueItemMapper), eq(1L), any())).thenReturn(first);
        mapperUtils.when(() -> MapperUtils.getById(eq(queueItemMapper), eq(2L), any())).thenReturn(second);
        when(queueItemMapper.start(any(), anyString(), any(LocalDateTime.class))).thenReturn(1);

        service.startGroup(List.of("1", "2"));

        ArgumentCaptor<String> batchNos = ArgumentCaptor.forClass(String.class);
        verify(queueItemMapper).start(eq(1L), batchNos.capture(), any(LocalDateTime.class));
        verify(queueItemMapper).start(eq(2L), batchNos.capture(), any(LocalDateTime.class));
        assertEquals(batchNos.getAllValues().get(0), batchNos.getAllValues().get(1));
    }

    @Test
    void completingProductionBatchCompletesEveryCookingItem() {
        KitchenQueueItemEntity first = queueItem(1L, 10L, 7L, "宫保鸡丁", "微辣", 1);
        KitchenQueueItemEntity second = queueItem(2L, 11L, 7L, "宫保鸡丁", "微辣", 2);
        for (KitchenQueueItemEntity item : List.of(first, second)) {
            item.setStatus("COOKING");
            item.setProductionBatchNo("PB-001");
        }
        mapperUtils.when(() -> MapperUtils.selectList(eq(queueItemMapper), eq(KitchenQueueItemEntity.class), any()))
                .thenReturn(List.of(first, second));
        when(queueItemMapper.completeProductionBatch(eq("PB-001"), any(LocalDateTime.class))).thenReturn(2);

        service.completeProductionBatches(List.of("PB-001"));

        verify(queueItemMapper).completeProductionBatch(eq("PB-001"), any(LocalDateTime.class));
    }

    @Test
    void printDispatchCreatesOneJobAndMarksEveryOrderItem() {
        List<KitchenQueueItemEntity> items = List.of(
                printQueueItem(1L, 10L, 7L, "鱼香肉丝", 1),
                printQueueItem(2L, 10L, 8L, "米饭", 2)
        );
        OrderEntity order = order(10L, "D001");
        mapperUtils.when(() -> MapperUtils.selectList(eq(queueItemMapper), eq(KitchenQueueItemEntity.class), any()))
                .thenReturn(items);
        mapperUtils.when(() -> MapperUtils.getById(eq(orderMapper), eq(10L), any())).thenReturn(order);
        when(ticketNumberRegistry.resolve(any())).thenReturn("大堂1号");
        when(printGatewayRegistry.print(any())).thenReturn(new KitchenPrintResult("job-1"));
        when(queueItemMapper.markPrinted(eq(1L), eq("job-1"), any())).thenReturn(1);
        when(queueItemMapper.markPrinted(eq(2L), eq("job-1"), any())).thenReturn(1);

        var result = service.printOrder(10L);

        assertEquals("job-1", result.jobId());
        assertEquals(2, result.itemCount());
        verify(queueItemMapper).markPrinted(eq(1L), eq("job-1"), any());
        verify(queueItemMapper).markPrinted(eq(2L), eq("job-1"), any());
    }

    private void mockQueueList(List<KitchenQueueItemEntity> items) {
        mapperUtils.when(() -> MapperUtils.selectList(eq(queueItemMapper), eq(KitchenQueueItemEntity.class), any()))
                .thenReturn(items);
    }

    private OrderEntity order(Long id, String orderNo) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setTableId(1L);
        order.setSource("TABLE");
        order.setStatus("CONFIRMED");
        return order;
    }

    private KitchenQueueItemEntity queueItem(
            Long id,
            Long orderId,
            Long menuItemId,
            String name,
            String specification,
            int quantity
    ) {
        KitchenQueueItemEntity item = new KitchenQueueItemEntity();
        item.setId(id);
        item.setOrderId(orderId);
        item.setOrderItemId(id + 100);
        item.setMenuItemId(menuItemId);
        item.setOrderNo("D" + orderId);
        item.setItemName(name);
        item.setSpecification(specification);
        item.setQuantity(quantity);
        item.setDispatchMode("PAPERLESS");
        item.setStatus("WAITING");
        item.setConfirmedAt(LocalDateTime.of(2026, 7, 14, 12, 0).plusSeconds(id));
        return item;
    }

    private KitchenQueueItemEntity printQueueItem(
            Long id,
            Long orderId,
            Long menuItemId,
            String name,
            int quantity
    ) {
        KitchenQueueItemEntity item = queueItem(id, orderId, menuItemId, name, null, quantity);
        item.setDispatchMode("PRINT");
        return item;
    }
}
