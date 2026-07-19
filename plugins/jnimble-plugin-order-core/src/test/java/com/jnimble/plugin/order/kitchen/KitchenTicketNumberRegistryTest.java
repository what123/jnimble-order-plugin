package com.jnimble.plugin.order.kitchen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class KitchenTicketNumberRegistryTest {

    @Test
    void usesFirstHookValueWhenAvailable() {
        KitchenTicketNumberHook empty = context -> Optional.empty();
        KitchenTicketNumberHook table = context -> Optional.of("大堂1号");
        KitchenTicketNumberRegistry registry = new KitchenTicketNumberRegistry();
        registry.register("empty", empty, 10);
        registry.register("order-table", table, 20);

        assertEquals("大堂1号", registry.resolve(context()));
    }

    @Test
    void fallsBackToOrderNumber() {
        KitchenTicketNumberRegistry registry = new KitchenTicketNumberRegistry();

        assertEquals("D001", registry.resolve(context()));
    }

    @Test
    void unregisterRemovesPluginContribution() {
        KitchenTicketNumberRegistry registry = new KitchenTicketNumberRegistry();
        var handle = registry.register("order-table", context -> Optional.of("大堂1号"), 100);
        assertEquals("大堂1号", registry.resolve(context()));

        handle.unregister();

        assertEquals("D001", registry.resolve(context()));
    }

    private KitchenTicketNumberContext context() {
        return new KitchenTicketNumberContext(1L, "D001", 8L, 20L, "TABLE");
    }
}
