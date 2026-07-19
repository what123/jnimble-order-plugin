package com.jnimble.plugin.order.table;

import com.jnimble.sdk.hook.HookRegistry;
import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.plugin.PluginDescriptor;
import com.jnimble.sdk.resource.AssetRegistry;
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
class OrderTablePluginBootTest {

    @Mock
    private PluginContext context;

    @Mock
    private HookRegistry hookRegistry;

    @Mock
    private RouteRegistry routeRegistry;

    @Mock
    private AssetRegistry assetRegistry;

    @Mock
    private PluginDescriptor descriptor;

    private OrderTablePluginBoot pluginBoot;

    @BeforeEach
    void setUp() {
        pluginBoot = new OrderTablePluginBoot();
        lenient().when(context.hooks()).thenReturn(hookRegistry);
        lenient().when(context.routes()).thenReturn(routeRegistry);
        lenient().when(context.assets()).thenReturn(assetRegistry);
        lenient().when(context.descriptor()).thenReturn(descriptor);
        lenient().when(descriptor.id()).thenReturn("order-table");
    }

    @Test
    void bootRegistersTableManagementInMenuGroup() {
        pluginBoot.boot(context);

        ArgumentCaptor<HookViewContribution> captor = ArgumentCaptor.forClass(HookViewContribution.class);
        verify(hookRegistry, times(2)).register(eq("admin.layout.sidebar"), captor.capture());
        List<HookViewContribution> contributions = captor.getAllValues();
        HookViewContribution posContribution = contributions.get(0);
        HookViewContribution tableContribution = contributions.get(1);

        assertEquals("plugin/order-table/fragment/sidebar-pos", posContribution.view());
        assertEquals(5, posContribution.order());
        assertEquals("plugin/order-table/fragment/sidebar-tables", tableContribution.view());
        assertEquals(22, tableContribution.order());
        assertEquals("order-table.admin.view", tableContribution.permission());
        assertEquals("order-table", tableContribution.model().get("pluginId"));
        assertNull(tableContribution.activeWhen());
    }

    @Test
    void cashierOrderingMenuOpensInNewTab() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/templates/plugin/order-table/fragment/sidebar-pos.html"
        )) {
            assertNotNull(input);
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(template.contains("target=\"_blank\""));
            assertTrue(template.contains("rel=\"noopener noreferrer\""));
        }
    }
}
