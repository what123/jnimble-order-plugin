package com.jnimble.plugin.order.table.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.payment.OrderPaymentResult;
import com.jnimble.plugin.order.payment.OrderPaymentStatus;
import com.jnimble.plugin.order.service.OrderService;
import com.jnimble.plugin.order.table.mapper.CheckoutMapper;
import com.jnimble.plugin.order.table.mapper.DiningSessionMapper;
import com.jnimble.plugin.order.table.mapper.TableOccupancyMapper;
import com.jnimble.plugin.order.table.mapper.TableOperationLogMapper;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutResult;
import com.jnimble.plugin.order.table.model.entity.CheckoutEntity;
import com.jnimble.plugin.order.table.model.entity.DiningSessionEntity;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import com.jnimble.plugin.order.table.model.entity.TableOccupancyEntity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiningSessionServiceTest {

    @Mock
    private TableService tableService;

    @Mock
    private OrderService orderService;

    @Mock
    private DiningSessionMapper sessionMapper;

    @Mock
    private TableOccupancyMapper occupancyMapper;

    @Mock
    private CheckoutMapper checkoutMapper;

    @Mock
    private TableOperationLogMapper operationLogMapper;

    private DiningSessionService diningSessionService;
    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        mapperUtilsMock = mockStatic(MapperUtils.class);
        diningSessionService = new DiningSessionService(
                tableService, orderService, sessionMapper, occupancyMapper,
                checkoutMapper, operationLogMapper
        );
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    @Test
    void beginCheckoutUsesOrderAmountAndMovesSessionToChecking() {
        DiningSessionEntity session = session(21L, "DINING");
        session.setPrimaryTableId(1L);
        OrderEntity order = confirmedOrder();
        mapperUtilsMock.when(() -> MapperUtils.selectList(
                eq(checkoutMapper), eq(CheckoutEntity.class), any()
        )).thenReturn(List.of());
        mapperUtilsMock.when(() -> MapperUtils.getById(
                eq(sessionMapper), eq(21L), any()
        )).thenReturn(session);
        when(orderService.findActiveOrderBySessionId(21L)).thenReturn(order);
        when(orderService.getActiveOrderItems(10L)).thenReturn(List.of(orderItem(101L)));

        CheckoutEntity result = diningSessionService.beginCheckout(
                21L, " cashier ", " idem-21 "
        );

        assertEquals("CREATED", result.getStatus());
        assertEquals(new BigDecimal("100.00"), result.getPayableAmount());
        assertEquals("idem-21", result.getIdempotencyKey());
        assertEquals("cashier", result.getCreatedBy());
        assertNotNull(result.getCheckoutNo());

        ArgumentCaptor<DiningSessionEntity> sessionUpdate =
                ArgumentCaptor.forClass(DiningSessionEntity.class);
        mapperUtilsMock.verify(() -> MapperUtils.updateById(
                eq(sessionMapper), sessionUpdate.capture()
        ));
        assertEquals("CHECKING", sessionUpdate.getValue().getStatus());
        verify(orderService, never()).settleOrder(any(), any(), any(), any());
    }

    @Test
    void beginCheckoutAllowsZeroPayableOrderWithItems() {
        DiningSessionEntity session = session(21L, "DINING");
        session.setPrimaryTableId(1L);
        OrderEntity order = confirmedOrder();
        order.setTotalAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.ZERO);
        mapperUtilsMock.when(() -> MapperUtils.selectList(
                eq(checkoutMapper), eq(CheckoutEntity.class), any()
        )).thenReturn(List.of());
        mapperUtilsMock.when(() -> MapperUtils.getById(
                eq(sessionMapper), eq(21L), any()
        )).thenReturn(session);
        when(orderService.findActiveOrderBySessionId(21L)).thenReturn(order);
        when(orderService.getActiveOrderItems(10L)).thenReturn(List.of(orderItem(101L)));

        CheckoutEntity result = diningSessionService.beginCheckout(
                21L, "cashier", "idem-free"
        );

        assertEquals(BigDecimal.ZERO, result.getPayableAmount());
        assertEquals("CREATED", result.getStatus());
    }

    @Test
    void beginCheckoutRejectsOrderWithoutActiveItems() {
        DiningSessionEntity session = session(21L, "DINING");
        OrderEntity order = confirmedOrder();
        mapperUtilsMock.when(() -> MapperUtils.selectList(
                eq(checkoutMapper), eq(CheckoutEntity.class), any()
        )).thenReturn(List.of());
        mapperUtilsMock.when(() -> MapperUtils.getById(
                eq(sessionMapper), eq(21L), any()
        )).thenReturn(session);
        when(orderService.findActiveOrderBySessionId(21L)).thenReturn(order);
        when(orderService.getActiveOrderItems(10L)).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> diningSessionService.beginCheckout(21L, "cashier", "idem-empty"));

        assertEquals("Cannot checkout an empty order", error.getMessage());
    }

    @Test
    void beginCheckoutRejectsNegativePayableAmount() {
        DiningSessionEntity session = session(21L, "DINING");
        OrderEntity order = confirmedOrder();
        order.setFinalAmount(new BigDecimal("-1.00"));
        mapperUtilsMock.when(() -> MapperUtils.selectList(
                eq(checkoutMapper), eq(CheckoutEntity.class), any()
        )).thenReturn(List.of());
        mapperUtilsMock.when(() -> MapperUtils.getById(
                eq(sessionMapper), eq(21L), any()
        )).thenReturn(session);
        when(orderService.findActiveOrderBySessionId(21L)).thenReturn(order);
        when(orderService.getActiveOrderItems(10L)).thenReturn(List.of(orderItem(101L)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> diningSessionService.beginCheckout(21L, "cashier", "idem-negative"));

        assertEquals("Order payable amount is invalid", error.getMessage());
    }

    @Test
    void checkoutFailureReturnsSessionToDining() {
        CheckoutEntity checkout = checkout("C1");
        mapperUtilsMock.when(() -> MapperUtils.getById(
                eq(checkoutMapper), eq("C1"), any()
        )).thenReturn(checkout);

        diningSessionService.markCheckoutFailed("C1", "cashier", "支付失败");

        ArgumentCaptor<CheckoutEntity> checkoutUpdate =
                ArgumentCaptor.forClass(CheckoutEntity.class);
        mapperUtilsMock.verify(() -> MapperUtils.updateById(
                eq(checkoutMapper), checkoutUpdate.capture()
        ));
        assertEquals("FAILED", checkoutUpdate.getValue().getStatus());

        ArgumentCaptor<DiningSessionEntity> sessionUpdate =
                ArgumentCaptor.forClass(DiningSessionEntity.class);
        mapperUtilsMock.verify(() -> MapperUtils.updateById(
                eq(sessionMapper), sessionUpdate.capture()
        ));
        assertEquals(21L, sessionUpdate.getValue().getId());
        assertEquals("DINING", sessionUpdate.getValue().getStatus());
    }

    @Test
    void lastSessionCheckoutMovesTableToCleaning() {
        CheckoutEntity checkout = checkout("C2");
        DiningSessionEntity session = session(21L, "CHECKING");
        session.setPrimaryTableId(1L);
        TableOccupancyEntity current = occupancy(51L, 1L, 21L);
        stubCompletedCheckout(checkout, session, List.of(current), List.of());

        CheckoutResult result = diningSessionService.completeCheckout(
                "C2", "CASH", new BigDecimal("120.00"), succeededPayment(), "cashier"
        );

        assertEquals("PAID", result.status());
        verify(orderService).settleOrder(
                10L, "CASH", new BigDecimal("100.00"), new BigDecimal("120.00")
        );
        verify(tableService).updateRuntimeStatus(1L, "CLEANING", "CLEANING");
    }

    @Test
    void sharedTableCheckoutKeepsOtherSessionOccupied() {
        CheckoutEntity checkout = checkout("C3");
        DiningSessionEntity session = session(21L, "CHECKING");
        session.setPrimaryTableId(1L);
        TableOccupancyEntity current = occupancy(51L, 1L, 21L);
        TableOccupancyEntity other = occupancy(52L, 1L, 22L);
        stubCompletedCheckout(checkout, session, List.of(current), List.of(other));

        CheckoutResult result = diningSessionService.completeCheckout(
                "C3", "CASH", new BigDecimal("100.00"), succeededPayment(), "cashier"
        );

        assertEquals("PAID", result.status());
        verify(tableService).updateRuntimeStatus(1L, "OCCUPIED", "AVAILABLE");
        verify(tableService, never()).completeTurnover(1L);
    }

    @Test
    void cleaningTableCanCompleteTurnoverWhenNoSessionRemains() {
        TableEntity table = new TableEntity();
        table.setId(1L);
        table.setStatus("CLEANING");
        when(tableService.getTableForUpdate(1L)).thenReturn(table);
        mapperUtilsMock.when(() -> MapperUtils.selectList(
                eq(occupancyMapper), eq(TableOccupancyEntity.class), any()
        )).thenReturn(List.of());

        diningSessionService.completeTurnover(1L, "cashier");

        verify(tableService).completeTurnover(1L);
    }

    private void stubCompletedCheckout(CheckoutEntity checkout,
                                       DiningSessionEntity session,
                                       List<TableOccupancyEntity> currentSessionOccupancies,
                                       List<TableOccupancyEntity> remainingTableOccupancies) {
        mapperUtilsMock.when(() -> MapperUtils.getById(
                eq(checkoutMapper), eq(checkout.getCheckoutNo()), any()
        )).thenReturn(checkout);
        mapperUtilsMock.when(() -> MapperUtils.getById(
                eq(sessionMapper), eq(session.getId()), any()
        )).thenReturn(session);
        mapperUtilsMock.when(() -> MapperUtils.selectList(
                eq(occupancyMapper), eq(TableOccupancyEntity.class), any()
        )).thenReturn(currentSessionOccupancies, remainingTableOccupancies);
    }

    private DiningSessionEntity session(Long id, String status) {
        DiningSessionEntity session = new DiningSessionEntity();
        session.setId(id);
        session.setStatus(status);
        return session;
    }

    private CheckoutEntity checkout(String checkoutNo) {
        CheckoutEntity checkout = new CheckoutEntity();
        checkout.setCheckoutNo(checkoutNo);
        checkout.setSessionId(21L);
        checkout.setOrderId(10L);
        checkout.setStatus("CREATED");
        checkout.setPayableAmount(new BigDecimal("100.00"));
        checkout.setPaidAmount(BigDecimal.ZERO);
        checkout.setChangeAmount(BigDecimal.ZERO);
        return checkout;
    }

    private OrderEntity confirmedOrder() {
        OrderEntity order = new OrderEntity();
        order.setId(10L);
        order.setOrderNo("D202607140001");
        order.setStatus("CONFIRMED");
        order.setTotalAmount(new BigDecimal("110.00"));
        order.setDiscountAmount(new BigDecimal("10.00"));
        order.setFinalAmount(new BigDecimal("100.00"));
        return order;
    }

    private OrderItemEntity orderItem(Long id) {
        OrderItemEntity item = new OrderItemEntity();
        item.setId(id);
        item.setOrderId(10L);
        item.setStatus("NORMAL");
        return item;
    }

    private TableOccupancyEntity occupancy(Long id, Long tableId, Long sessionId) {
        TableOccupancyEntity occupancy = new TableOccupancyEntity();
        occupancy.setId(id);
        occupancy.setTableId(tableId);
        occupancy.setSessionId(sessionId);
        occupancy.setStatus("ACTIVE");
        return occupancy;
    }

    private OrderPaymentResult succeededPayment() {
        return new OrderPaymentResult(
                OrderPaymentStatus.SUCCEEDED, "PAY-1", "LOCAL-1",
                new BigDecimal("100.00"), BigDecimal.ZERO, null, null
        );
    }
}
