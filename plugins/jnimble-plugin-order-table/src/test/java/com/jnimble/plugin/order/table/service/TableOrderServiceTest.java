package com.jnimble.plugin.order.table.service;

import com.jnimble.plugin.menu.model.entity.CategoryEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecGroupEntity;
import com.jnimble.plugin.menu.model.entity.MenuItemSpecOptionEntity;
import com.jnimble.plugin.menu.service.CategoryService;
import com.jnimble.plugin.menu.service.MenuItemService;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.model.entity.OrderSubmissionEntity;
import com.jnimble.plugin.order.payment.OrderPaymentProviderRegistry;
import com.jnimble.plugin.order.payment.OrderPaymentRequest;
import com.jnimble.plugin.order.payment.OrderPaymentResult;
import com.jnimble.plugin.order.payment.OrderPaymentStatus;
import com.jnimble.plugin.order.service.OrderService;
import com.jnimble.plugin.order.service.OrderSubmissionService;
import com.jnimble.plugin.order.submission.OrderSubmissionView;
import com.jnimble.plugin.order.table.model.dto.PosModels.AddItemRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.CatalogResponse;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutResult;
import com.jnimble.plugin.order.table.model.dto.PosModels.PendingConfirmation;
import com.jnimble.plugin.order.table.model.dto.PosModels.SessionDetail;
import com.jnimble.plugin.order.table.model.entity.CheckoutEntity;
import com.jnimble.plugin.order.table.model.entity.DiningSessionEntity;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableOrderServiceTest {

    @Mock
    private DiningSessionService diningSessionService;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderSubmissionService submissionService;

    @Mock
    private MenuItemService menuItemService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private OrderPaymentProviderRegistry paymentProviders;

    @InjectMocks
    private TableOrderService tableOrderService;

    @Test
    void compatibilityOpenTableReturnsOrderFromCreatedSession() {
        OrderEntity order = order(10L, 21L, "D202607140001", "CONFIRMED");
        SessionDetail detail = sessionDetail(21L, "DINING", order);
        when(diningSessionService.openTable(1L, 4, "operator", null, false))
                .thenReturn(detail);

        OrderEntity result = tableOrderService.openTable(1L, 4, "operator");

        assertSame(order, result);
        verify(diningSessionService).openTable(1L, 4, "operator", null, false);
    }

    @Test
    void catalogOnlyReturnsEnabledCategoriesAndEnabledItems() {
        CategoryEntity enabled = new CategoryEntity();
        enabled.setStatus("ENABLED");
        CategoryEntity disabled = new CategoryEntity();
        disabled.setStatus("DISABLED");
        MenuItemEntity item = new MenuItemEntity();
        when(categoryService.listCategories()).thenReturn(List.of(disabled, enabled));
        when(menuItemService.listItems(null, null, "ENABLED")).thenReturn(List.of(item));

        CatalogResponse result = tableOrderService.catalog();

        assertEquals(List.of(enabled), result.categories());
        assertEquals(List.of(item), result.items());
    }

    @Test
    void addMenuItemUsesServerMenuAndSpecificationPrice() {
        OrderEntity order = order(10L, 21L, "D202607140001", "DRAFT");
        SessionDetail detail = sessionDetail(21L, "DINING", order);
        MenuItemEntity item = menuItemWithRequiredSize();
        AddItemRequest request = new AddItemRequest(20L, List.of(101L), 2, "  少辣  ");
        when(diningSessionService.getDetail(21L)).thenReturn(detail);
        when(menuItemService.getItem(20L)).thenReturn(item);

        SessionDetail result = tableOrderService.addMenuItem(21L, request);

        assertSame(detail, result);
        verify(orderService).addItem(
                10L, 20L, "宫保鸡丁", "份量: 大份",
                new BigDecimal("25.00"), 2, "少辣"
        );
    }

    @Test
    void addMenuItemRejectsSpecificationFromAnotherMenuItem() {
        OrderEntity order = order(10L, 21L, "D202607140001", "DRAFT");
        when(diningSessionService.getDetail(21L))
                .thenReturn(sessionDetail(21L, "DINING", order));
        when(menuItemService.getItem(20L)).thenReturn(menuItemWithRequiredSize());
        AddItemRequest request = new AddItemRequest(20L, List.of(999L), 1, null);

        assertThrows(IllegalArgumentException.class,
                () -> tableOrderService.addMenuItem(21L, request));

        verify(orderService, never()).addItem(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void successfulPaymentCompletesCheckoutWithServerAmount() {
        CheckoutEntity checkout = checkout("C202607140001", "CREATED");
        OrderEntity order = order(10L, 21L, "D202607140001", "CONFIRMED");
        order.setTableId(1L);
        CheckoutRequest request = new CheckoutRequest(
                "CASH", new BigDecimal("120.00"), " cashier ", "idem-21"
        );
        OrderPaymentResult paymentResult = new OrderPaymentResult(
                OrderPaymentStatus.SUCCEEDED, "PAY-1", "LOCAL-1",
                new BigDecimal("120.00"), new BigDecimal("20.00"), null, null
        );
        CheckoutResult completed = new CheckoutResult(
                checkout.getCheckoutNo(), "PAID", 21L, 10L,
                new BigDecimal("100.00"), new BigDecimal("120.00"),
                new BigDecimal("20.00"), "PAY-1"
        );
        when(diningSessionService.beginCheckout(21L, "cashier", "idem-21"))
                .thenReturn(checkout);
        when(diningSessionService.getDetail(21L))
                .thenReturn(sessionDetail(21L, "DINING", order));
        when(orderService.getOrder(10L)).thenReturn(order);
        when(paymentProviders.pay(any(OrderPaymentRequest.class))).thenReturn(paymentResult);
        when(diningSessionService.completeCheckout(
                checkout.getCheckoutNo(), "CASH", request.receivedAmount(), paymentResult, "cashier"
        )).thenReturn(completed);

        CheckoutResult result = tableOrderService.checkout(21L, request);

        assertSame(completed, result);
        ArgumentCaptor<OrderPaymentRequest> captor =
                ArgumentCaptor.forClass(OrderPaymentRequest.class);
        verify(paymentProviders).pay(captor.capture());
        assertEquals(new BigDecimal("100.00"), captor.getValue().amount());
        assertEquals("CASH", captor.getValue().method());
        assertEquals("idem-21", captor.getValue().idempotencyKey());
        assertEquals(21L, captor.getValue().metadata().get("sessionId"));
    }

    @Test
    void failedPaymentMarksCheckoutFailedAndDoesNotCompleteIt() {
        CheckoutEntity checkout = checkout("C202607140002", "CREATED");
        OrderEntity order = order(10L, 21L, "D202607140001", "CONFIRMED");
        order.setTableId(1L);
        CheckoutRequest request = new CheckoutRequest(
                "WECHAT", new BigDecimal("100.00"), "cashier", "idem-fail"
        );
        OrderPaymentResult failed = new OrderPaymentResult(
                OrderPaymentStatus.FAILED, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "DECLINED", "支付失败"
        );
        when(diningSessionService.beginCheckout(21L, "cashier", "idem-fail"))
                .thenReturn(checkout);
        when(diningSessionService.getDetail(21L))
                .thenReturn(sessionDetail(21L, "DINING", order));
        when(orderService.getOrder(10L)).thenReturn(order);
        when(paymentProviders.pay(any(OrderPaymentRequest.class))).thenReturn(failed);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> tableOrderService.checkout(21L, request));

        assertEquals("支付失败", error.getMessage());
        verify(diningSessionService)
                .markCheckoutFailed(checkout.getCheckoutNo(), "cashier", "支付失败");
        verify(diningSessionService, never()).completeCheckout(
                any(), any(), any(), any(), any()
        );
        verify(orderService, never()).settleOrder(any(), any(), any(), any());
    }

    @Test
    void paidIdempotentCheckoutDoesNotPayTwice() {
        CheckoutEntity checkout = checkout("C202607140003", "PAID");
        checkout.setPaidAmount(new BigDecimal("100.00"));
        checkout.setChangeAmount(BigDecimal.ZERO);
        checkout.setPaymentId("PAY-1");
        CheckoutRequest request = new CheckoutRequest(
                "CASH", new BigDecimal("100.00"), "cashier", "idem-paid"
        );
        OrderEntity order = order(10L, 21L, "D202607140001", "SETTLED");
        when(diningSessionService.getDetail(21L))
                .thenReturn(sessionDetail(21L, "CLOSED", order));
        when(diningSessionService.beginCheckout(21L, "cashier", "idem-paid"))
                .thenReturn(checkout);

        CheckoutResult result = tableOrderService.checkout(21L, request);

        assertEquals("PAID", result.status());
        assertEquals("PAY-1", result.paymentId());
        verify(paymentProviders, never()).pay(any());
    }

    @Test
    void pendingConfirmationsIncludeTableAndBatchDetails() {
        OrderEntity order = order(10L, 21L, "D202607140001", "DRAFT");
        OrderSubmissionEntity submission = new OrderSubmissionEntity();
        submission.setId(50L);
        submission.setOrderId(10L);
        submission.setBatchSeq(1);
        submission.setBatchType("INITIAL");
        submission.setSourceType("CUSTOMER_SCAN");
        submission.setStatus("PENDING");
        submission.setSubmittedAt(LocalDateTime.now());
        OrderItemEntity item = new OrderItemEntity();
        item.setId(100L);
        item.setItemName("宫保鸡丁");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("38.00"));
        item.setSubtotal(new BigDecimal("76.00"));
        item.setStatus("NORMAL");
        TableEntity table = new TableEntity();
        table.setId(1L);
        table.setTableName("大堂1号");
        SessionDetail detail = sessionDetail(21L, "DINING", order, List.of(table), List.of(item));
        when(submissionService.listPending())
                .thenReturn(List.of(new OrderSubmissionView(submission, order, List.of(item))));
        when(diningSessionService.getDetail(21L)).thenReturn(detail);

        List<PendingConfirmation> result = tableOrderService.pendingConfirmations();

        assertEquals(1, result.size());
        assertEquals("大堂1号", result.getFirst().tableNames().getFirst());
        assertEquals(new BigDecimal("76.00"), result.getFirst().amount());
        assertEquals(2, result.getFirst().quantity());
    }

    @Test
    void confirmSubmissionReturnsUpdatedDiningSession() {
        OrderEntity order = order(10L, 21L, "D202607140001", "CONFIRMED");
        OrderSubmissionEntity submission = new OrderSubmissionEntity();
        submission.setId(50L);
        submission.setOrderId(10L);
        submission.setBatchSeq(1);
        SessionDetail detail = sessionDetail(21L, "DINING", order);
        when(submissionService.confirm(50L, "cashier"))
                .thenReturn(new OrderSubmissionView(submission, order, List.of()));
        when(diningSessionService.getDetail(21L)).thenReturn(detail);

        SessionDetail result = tableOrderService.confirmSubmission(50L, "cashier");

        assertSame(detail, result);
        verify(submissionService).confirm(50L, "cashier");
    }

    @Test
    void pendingCustomerSubmissionCannotBeBypassedByDirectOrderConfirmation() {
        OrderEntity order = order(10L, 21L, "D202607140001", "DRAFT");
        when(diningSessionService.getDetail(21L))
                .thenReturn(sessionDetail(21L, "DINING", order));
        when(submissionService.hasPendingForOrder(10L)).thenReturn(true);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> tableOrderService.confirmOrder(21L)
        );

        assertEquals("Order has a submission waiting for staff confirmation", error.getMessage());
        verify(orderService, never()).confirmOrder(any());
    }

    private SessionDetail sessionDetail(Long sessionId, String status, OrderEntity order) {
        return sessionDetail(sessionId, status, order, List.of(), List.of());
    }

    private SessionDetail sessionDetail(Long sessionId, String status, OrderEntity order,
                                        List<TableEntity> tables, List<OrderItemEntity> items) {
        DiningSessionEntity session = new DiningSessionEntity();
        session.setId(sessionId);
        session.setStatus(status);
        return new SessionDetail(session, null, tables, order, items);
    }

    private MenuItemEntity menuItemWithRequiredSize() {
        MenuItemSpecOptionEntity large = new MenuItemSpecOptionEntity();
        large.setId(101L);
        large.setName("大份");
        large.setStatus("ENABLED");
        large.setPriceAdjust(new BigDecimal("5.00"));

        MenuItemSpecGroupEntity size = new MenuItemSpecGroupEntity();
        size.setName("份量");
        size.setRequired(true);
        size.setMulti(false);
        size.setOptions(List.of(large));

        MenuItemEntity item = new MenuItemEntity();
        item.setId(20L);
        item.setName("宫保鸡丁");
        item.setStatus("ENABLED");
        item.setPrice(new BigDecimal("20.00"));
        item.setGroups(List.of(size));
        return item;
    }

    private OrderEntity order(Long id, Long sessionId, String orderNo, String status) {
        OrderEntity order = new OrderEntity();
        order.setId(id);
        order.setSessionId(sessionId);
        order.setOrderNo(orderNo);
        order.setStatus(status);
        return order;
    }

    private CheckoutEntity checkout(String checkoutNo, String status) {
        CheckoutEntity checkout = new CheckoutEntity();
        checkout.setCheckoutNo(checkoutNo);
        checkout.setSessionId(21L);
        checkout.setOrderId(10L);
        checkout.setStatus(status);
        checkout.setPayableAmount(new BigDecimal("100.00"));
        checkout.setPaidAmount(BigDecimal.ZERO);
        checkout.setChangeAmount(BigDecimal.ZERO);
        checkout.setIdempotencyKey(status.equals("PAID") ? "idem-paid" : "idem-21");
        return checkout;
    }
}
