package com.jnimble.plugin.order.kitchen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class KitchenExtensionRegistryTest {

    @Test
    void dispatchModeContributionOnlyAppliesWhileRegistered() {
        KitchenDispatchModeRegistry registry = new KitchenDispatchModeRegistry();
        KitchenTicketNumberContext context = context();
        assertEquals(KitchenDispatchMode.PAPERLESS, registry.resolve(context));

        var handle = registry.register(
                "printer-core",
                ignored -> Optional.of(KitchenDispatchMode.PRINT),
                100
        );
        assertEquals(KitchenDispatchMode.PRINT, registry.resolve(context));

        handle.unregister();
        assertEquals(KitchenDispatchMode.PAPERLESS, registry.resolve(context));
    }

    @Test
    void printGatewayContributionOnlyAppliesWhileRegistered() {
        KitchenPrintGatewayRegistry registry = new KitchenPrintGatewayRegistry();
        assertThrows(IllegalStateException.class, () -> registry.print(null));

        var handle = registry.register("printer-core", ignored -> new KitchenPrintResult("job-1"));
        assertEquals("job-1", registry.print(null).jobId());

        handle.unregister();
        assertThrows(IllegalStateException.class, () -> registry.print(null));
    }

    private KitchenTicketNumberContext context() {
        return new KitchenTicketNumberContext(1L, "D001", 8L, 20L, "TABLE");
    }
}

