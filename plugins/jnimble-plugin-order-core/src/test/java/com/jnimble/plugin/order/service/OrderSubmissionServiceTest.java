package com.jnimble.plugin.order.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.mapper.OrderSubmissionMapper;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.model.entity.OrderSubmissionEntity;
import com.jnimble.plugin.order.submission.OrderConfirmationMode;
import com.jnimble.plugin.order.submission.OrderSubmissionCommand;
import com.jnimble.plugin.order.submission.OrderSubmissionView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSubmissionServiceTest {

    @Mock
    private OrderSubmissionMapper submissionMapper;

    @Mock
    private OrderService orderService;

    private OrderSubmissionService submissionService;
    private MockedStatic<MapperUtils> mapperUtils;

    @BeforeEach
    void setUp() {
        mapperUtils = mockStatic(MapperUtils.class);
        submissionService = new OrderSubmissionService(submissionMapper, orderService);
        mapperUtils.when(() -> MapperUtils.insert(eq(submissionMapper), any(OrderSubmissionEntity.class)))
                .thenAnswer(invocation -> {
                    OrderSubmissionEntity entity = invocation.getArgument(1);
                    entity.setId(50L);
                    return entity;
                });
        mapperUtils.when(() -> MapperUtils.updateById(eq(submissionMapper), any(OrderSubmissionEntity.class)))
                .thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        mapperUtils.close();
    }

    @Test
    void staffInitialSubmissionWaitsWithoutDispatching() {
        OrderEntity order = order(1L, "DRAFT");
        OrderItemEntity item = item(11L, 1);
        when(orderService.prepareInitialBatch(1L)).thenReturn(List.of(item));
        when(orderService.getOrder(1L)).thenReturn(order);
        when(orderService.getOrderItemsByBatch(1L, 1)).thenReturn(List.of(item));

        OrderSubmissionView result = submissionService.submitInitial(
                1L, command(OrderConfirmationMode.STAFF, "initial-1")
        );

        assertEquals("PENDING", result.submission().getStatus());
        assertEquals("INITIAL", result.submission().getBatchType());
        verify(orderService, never()).confirmBatch(any(), any());
    }

    @Test
    void automaticInitialSubmissionConfirmsImmediately() {
        OrderEntity order = order(1L, "CONFIRMED");
        OrderItemEntity item = item(11L, 1);
        when(orderService.prepareInitialBatch(1L)).thenReturn(List.of(item));
        when(orderService.getOrder(1L)).thenReturn(order);
        when(orderService.getOrderItemsByBatch(1L, 1)).thenReturn(List.of(item));

        OrderSubmissionView result = submissionService.submitInitial(
                1L, command(OrderConfirmationMode.AUTO, "initial-auto")
        );

        assertEquals("CONFIRMED", result.submission().getStatus());
        verify(orderService).confirmBatch(1L, 1);
    }

    @Test
    void staffAddOnStagesItemsUntilConfirmation() {
        OrderEntity order = order(1L, "CONFIRMED");
        OrderItemEntity item = item(12L, 2);
        when(orderService.getOrder(1L)).thenReturn(order);
        when(orderService.stageBatchItems(eq(1L), any())).thenReturn(List.of(item));
        when(orderService.getOrderItemsByBatch(1L, 2)).thenReturn(List.of(item));

        OrderSubmissionView result = submissionService.submitItems(
                1L, List.of(item), command(OrderConfirmationMode.STAFF, "add-1")
        );

        assertEquals("PENDING", result.submission().getStatus());
        assertEquals("ADD_ON", result.submission().getBatchType());
        verify(orderService, never()).confirmBatch(any(), any());
    }

    @Test
    void confirmingPendingSubmissionDispatchesOnlyItsBatch() {
        OrderSubmissionEntity submission = submission(50L, 1L, 2, "ADD_ON", "PENDING");
        when(submissionMapper.selectByIdForUpdate(50L)).thenReturn(submission);
        when(orderService.getOrder(1L)).thenReturn(order(1L, "CONFIRMED"));
        when(orderService.getOrderItemsByBatch(1L, 2)).thenReturn(List.of(item(12L, 2)));

        OrderSubmissionView result = submissionService.confirm(50L, "cashier");

        assertEquals("CONFIRMED", result.submission().getStatus());
        assertEquals("cashier", result.submission().getConfirmedBy());
        verify(orderService).confirmBatch(1L, 2);
    }

    @Test
    void repeatedConfirmationIsIdempotent() {
        OrderSubmissionEntity submission = submission(50L, 1L, 2, "ADD_ON", "CONFIRMED");
        when(submissionMapper.selectByIdForUpdate(50L)).thenReturn(submission);
        when(orderService.getOrder(1L)).thenReturn(order(1L, "CONFIRMED"));
        when(orderService.getOrderItemsByBatch(1L, 2)).thenReturn(List.of(item(12L, 2)));

        submissionService.confirm(50L, "cashier");

        verify(orderService, never()).confirmBatch(any(), any());
    }

    @Test
    void returningAddOnCancelsItemsAndRestoresAmount() {
        OrderSubmissionEntity submission = submission(50L, 1L, 2, "ADD_ON", "PENDING");
        when(submissionMapper.selectByIdForUpdate(50L)).thenReturn(submission);
        when(orderService.getOrder(1L)).thenReturn(order(1L, "CONFIRMED"));
        when(orderService.getOrderItemsByBatch(1L, 2)).thenReturn(List.of(item(12L, 2)));

        OrderSubmissionView result = submissionService.returnSubmission(50L, "cashier", "售罄");

        assertEquals("RETURNED", result.submission().getStatus());
        assertEquals("售罄", result.submission().getReturnReason());
        verify(orderService).cancelAddOnBatch(1L, 2);
    }

    @Test
    void duplicateIdempotencyKeyReturnsOriginalSubmission() {
        OrderSubmissionEntity submission = submission(50L, 1L, 1, "INITIAL", "PENDING");
        submission.setIdempotencyKey("initial-1");
        when(submissionMapper.selectByIdempotencyKey("initial-1")).thenReturn(submission);
        when(orderService.getOrder(1L)).thenReturn(order(1L, "DRAFT"));
        when(orderService.getOrderItemsByBatch(1L, 1)).thenReturn(List.of(item(11L, 1)));

        OrderSubmissionView result = submissionService.submitInitial(
                1L, command(OrderConfirmationMode.STAFF, "initial-1")
        );

        assertEquals(50L, result.submission().getId());
        verify(orderService, never()).prepareInitialBatch(any());
    }

    private OrderSubmissionCommand command(OrderConfirmationMode mode, String key) {
        return new OrderSubmissionCommand("CUSTOMER_SCAN", mode, "guest", key);
    }

    private OrderEntity order(Long id, String status) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setOrderNo("D202607150001");
        order.setStatus(status);
        return order;
    }

    private OrderItemEntity item(Long id, int batchSeq) {
        OrderItemEntity item = new OrderItemEntity();
        item.setId(id);
        item.setOrderId(1L);
        item.setItemName("宫保鸡丁");
        item.setUnitPrice(new BigDecimal("38.00"));
        item.setQuantity(1);
        item.setSubtotal(new BigDecimal("38.00"));
        item.setStatus("NORMAL");
        item.setBatchSeq(batchSeq);
        return item;
    }

    private OrderSubmissionEntity submission(Long id, Long orderId, int batchSeq,
                                               String type, String status) {
        OrderSubmissionEntity entity = new OrderSubmissionEntity();
        entity.setId(id);
        entity.setOrderId(orderId);
        entity.setBatchSeq(batchSeq);
        entity.setBatchType(type);
        entity.setSourceType("CUSTOMER_SCAN");
        entity.setConfirmationMode("STAFF");
        entity.setStatus(status);
        entity.setSubmittedAt(LocalDateTime.now());
        return entity;
    }
}
