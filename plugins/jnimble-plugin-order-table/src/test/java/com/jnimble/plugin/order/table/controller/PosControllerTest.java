package com.jnimble.plugin.order.table.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.order.table.model.dto.PosModels.AddItemRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.CatalogResponse;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutResult;
import com.jnimble.plugin.order.table.model.dto.PosModels.ConfirmationActionRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.OpenSessionRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.QuantityRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.ReturnConfirmationRequest;
import com.jnimble.plugin.order.table.model.dto.PosModels.SessionDetail;
import com.jnimble.plugin.order.table.model.dto.PosModels.TableBoardItem;
import com.jnimble.plugin.order.table.service.TableOrderService;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosControllerTest {

    @Mock
    private TableOrderService tableOrderService;

    @Mock
    private ControllerAuthorization authorization;

    @InjectMocks
    private PosController posController;

    @Test
    void posLayoutExposesCurrentOperator() {
        Map<String, Object> model = new HashMap<>();
        Principal principal = () -> "cashier";
        when(authorization.hasPermission("order-table.pos.confirm")).thenReturn(true);

        String view = posController.posLayout(model, principal);

        assertEquals("plugin/order-table/pos/layout", view);
        assertEquals("cashier", model.get("operator"));
        assertEquals(true, model.get("canConfirmOrders"));
        verify(authorization).requirePermission("order-table.pos.view");
    }

    @Test
    void boardRequiresViewPermissionAndDelegates() {
        List<TableBoardItem> board = List.of();
        when(tableOrderService.board()).thenReturn(board);

        Object result = posController.board();

        assertSame(board, result);
        verify(authorization).requirePermission("order-table.pos.view");
        verify(tableOrderService).board();
    }

    @Test
    void catalogRequiresViewPermissionAndDelegates() {
        CatalogResponse catalog = new CatalogResponse(List.of(), List.of());
        when(tableOrderService.catalog()).thenReturn(catalog);

        Object result = posController.catalog();

        assertSame(catalog, result);
        verify(authorization).requirePermission("order-table.pos.view");
        verify(tableOrderService).catalog();
    }

    @Test
    void pendingConfirmationsRequireViewPermission() {
        when(tableOrderService.pendingConfirmations()).thenReturn(List.of());

        Object result = posController.pendingConfirmations();

        assertEquals(List.of(), result);
        verify(authorization).requirePermission("order-table.pos.view");
        verify(tableOrderService).pendingConfirmations();
    }

    @Test
    void confirmationActionsRequireConfirmPermission() {
        SessionDetail detail = emptySessionDetail();
        ConfirmationActionRequest confirm = new ConfirmationActionRequest("cashier");
        ReturnConfirmationRequest returned = new ReturnConfirmationRequest("cashier", "售罄");
        when(tableOrderService.confirmSubmission(50L, "cashier")).thenReturn(detail);
        when(tableOrderService.returnSubmission(51L, "cashier", "售罄")).thenReturn(detail);

        assertSame(detail, posController.confirmSubmission(50L, confirm));
        assertSame(detail, posController.returnSubmission(51L, returned));

        verify(authorization, times(2)).requirePermission("order-table.pos.confirm");
        verify(tableOrderService).confirmSubmission(50L, "cashier");
        verify(tableOrderService).returnSubmission(51L, "cashier", "售罄");
    }

    @Test
    void sessionRequiresViewPermissionAndDelegates() {
        SessionDetail detail = emptySessionDetail();
        when(tableOrderService.getSession(21L)).thenReturn(detail);

        Object result = posController.session(21L);

        assertSame(detail, result);
        verify(authorization).requirePermission("order-table.pos.view");
        verify(tableOrderService).getSession(21L);
    }

    @Test
    void openTableUsesSessionRequestAndOpenPermission() {
        OpenSessionRequest request = new OpenSessionRequest(4, "cashier", "靠窗");
        SessionDetail detail = emptySessionDetail();
        when(tableOrderService.openTable(1L, 4, "cashier", "靠窗", false))
                .thenReturn(detail);

        Object result = posController.openTable(1L, request);

        assertSame(detail, result);
        verify(authorization).requirePermission("order-table.pos.open");
        verify(tableOrderService).openTable(1L, 4, "cashier", "靠窗", false);
    }

    @Test
    void addAndUpdateItemUseSessionScopedApi() {
        AddItemRequest addRequest = new AddItemRequest(8L, List.of(81L), 2, "少辣");
        QuantityRequest quantityRequest = new QuantityRequest(3);
        SessionDetail detail = emptySessionDetail();
        when(tableOrderService.addMenuItem(21L, addRequest)).thenReturn(detail);
        when(tableOrderService.updateItemQuantity(21L, 31L, 3)).thenReturn(detail);

        assertSame(detail, posController.addItem(21L, addRequest));
        assertSame(detail, posController.updateItemQuantity(21L, 31L, quantityRequest));

        verify(authorization, times(2)).requirePermission("order-table.pos.view");
        verify(tableOrderService).addMenuItem(21L, addRequest);
        verify(tableOrderService).updateItemQuantity(21L, 31L, 3);
    }

    @Test
    void removeAndConfirmOrderUseSessionScopedApi() {
        SessionDetail detail = emptySessionDetail();
        when(tableOrderService.removeItem(21L, 31L)).thenReturn(detail);
        when(tableOrderService.confirmOrder(21L)).thenReturn(detail);

        assertSame(detail, posController.removeItem(21L, 31L));
        assertSame(detail, posController.confirmOrder(21L));

        verify(authorization, times(2)).requirePermission("order-table.pos.view");
        verify(tableOrderService).removeItem(21L, 31L);
        verify(tableOrderService).confirmOrder(21L);
    }

    @Test
    void checkoutRequiresSettlePermissionAndDelegates() {
        CheckoutRequest request = new CheckoutRequest(
                "CASH", new BigDecimal("120.00"), "cashier", "checkout-21"
        );
        CheckoutResult checkout = new CheckoutResult(
                "C202607140001", "PAID", 21L, 41L,
                new BigDecimal("100.00"), new BigDecimal("120.00"),
                new BigDecimal("20.00"), "PAY-1"
        );
        when(tableOrderService.checkout(21L, request)).thenReturn(checkout);

        Object result = posController.checkout(21L, request);

        assertSame(checkout, result);
        verify(authorization).requirePermission("order-table.pos.settle");
        verify(tableOrderService).checkout(21L, request);
    }

    private SessionDetail emptySessionDetail() {
        return new SessionDetail(null, null, List.of(), null, List.of());
    }
}
