package com.jnimble.plugin.printer.kitchen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.jnimble.plugin.order.kitchen.KitchenDispatchMode;
import com.jnimble.plugin.order.kitchen.KitchenTicketNumberContext;
import com.jnimble.plugin.printer.model.entity.PrintNodeEntity;
import com.jnimble.plugin.printer.service.PrintNodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrinterKitchenDispatchModeHookTest {

    @Mock
    private PrintNodeService printNodeService;

    @Test
    void usesPrintModeFromFixedOrderConfirmedNode() {
        PrintNodeEntity node = new PrintNodeEntity();
        node.setEnabled(true);
        node.setOperationMode("PRINT");
        when(printNodeService.getNodeByName("order.confirmed")).thenReturn(node);
        PrinterKitchenDispatchModeHook hook = new PrinterKitchenDispatchModeHook(printNodeService);

        assertEquals(KitchenDispatchMode.PRINT, hook.resolve(context()).orElseThrow());
    }

    @Test
    void disabledNodeDoesNotOverridePaperlessDefault() {
        PrintNodeEntity node = new PrintNodeEntity();
        node.setEnabled(false);
        when(printNodeService.getNodeByName("order.confirmed")).thenReturn(node);
        PrinterKitchenDispatchModeHook hook = new PrinterKitchenDispatchModeHook(printNodeService);

        assertTrue(hook.resolve(context()).isEmpty());
    }

    private KitchenTicketNumberContext context() {
        return new KitchenTicketNumberContext(1L, "D001", null, null, "NUMBER");
    }
}

