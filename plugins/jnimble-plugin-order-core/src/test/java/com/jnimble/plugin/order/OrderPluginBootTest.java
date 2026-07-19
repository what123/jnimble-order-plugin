package com.jnimble.plugin.order;

import com.jnimble.sdk.hook.HookRegistry;
import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.plugin.PluginDescriptor;
import com.jnimble.sdk.route.RouteRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPluginBootTest {

    @Mock
    private PluginContext context;

    @Mock
    private HookRegistry hookRegistry;

    @Mock
    private RouteRegistry routeRegistry;

    @Mock
    private PluginDescriptor descriptor;

    private OrderPluginBoot orderPluginBoot;

    @BeforeEach
    void setUp() {
        orderPluginBoot = new OrderPluginBoot();
        lenient().when(context.hooks()).thenReturn(hookRegistry);
        lenient().when(context.routes()).thenReturn(routeRegistry);
        lenient().when(context.descriptor()).thenReturn(descriptor);
        lenient().when(descriptor.id()).thenReturn("order-core");
    }

    @Test
    void bootRegistersKitchenAndOrderManagementSidebarItems() {
        orderPluginBoot.boot(context);

        ArgumentCaptor<HookViewContribution> captor = ArgumentCaptor.forClass(HookViewContribution.class);
        verify(hookRegistry, times(2)).register(eq("admin.layout.sidebar"), captor.capture());
        List<HookViewContribution> contributions = captor.getAllValues();
        HookViewContribution kitchen = contributions.get(0);
        HookViewContribution orders = contributions.get(1);

        assertEquals("plugin/order-core/fragment/sidebar-kitchen", kitchen.view());
        assertEquals(12, kitchen.order());
        assertEquals("order-core.kitchen.view", kitchen.permission());
        assertEquals("order-core", kitchen.model().get("pluginId"));
        assertNull(kitchen.activeWhen());

        assertEquals("plugin/order-core/fragment/sidebar-orders", orders.view());
        assertEquals(15, orders.order());
        assertEquals("order-core.admin.view", orders.permission());
        assertEquals("order-core", orders.model().get("pluginId"));
        assertNull(orders.activeWhen());
    }

    @Test
    void kitchenMenuOpensInNewTab() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/templates/plugin/order-core/fragment/sidebar-kitchen.html"
        )) {
            assertNotNull(input);
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(template.contains("target=\"_blank\""));
            assertTrue(template.contains("rel=\"noopener noreferrer\""));
        }
    }
}
