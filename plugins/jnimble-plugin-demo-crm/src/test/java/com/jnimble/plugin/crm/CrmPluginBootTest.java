package com.jnimble.plugin.crm;

import com.jnimble.sdk.hook.HookRegistry;
import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.plugin.PluginDescriptor;
import com.jnimble.sdk.resource.AssetRegistry;
import com.jnimble.sdk.resource.AssetDefinition;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CrmPluginBootTest {

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

    private CrmPluginBoot crmPluginBoot;

    @BeforeEach
    void setUp() {
        crmPluginBoot = new CrmPluginBoot();
        lenient().when(context.hooks()).thenReturn(hookRegistry);
        lenient().when(context.routes()).thenReturn(routeRegistry);
        lenient().when(context.assets()).thenReturn(assetRegistry);
        lenient().when(context.descriptor()).thenReturn(descriptor);
        lenient().when(descriptor.id()).thenReturn("crm");
    }

    @Test
    void testBootRegistersHook() {
        crmPluginBoot.boot(context);

        ArgumentCaptor<HookViewContribution> hookCaptor = ArgumentCaptor.forClass(HookViewContribution.class);
        verify(hookRegistry).register(eq("admin.layout.sidebar"), hookCaptor.capture());

        HookViewContribution contribution = hookCaptor.getValue();
        assertEquals("plugin/crm/fragment/sidebar", contribution.view());
        assertEquals(40, contribution.order());
        assertEquals("crm.customer.view", contribution.permission());
        assertNull(contribution.activeWhen());
        assertEquals("crm", contribution.model().get("pluginId"));
    }

    @Test
    void testBootRegistersRoute() {
        crmPluginBoot.boot(context);

        ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
        verify(routeRegistry).register(routeCaptor.capture());

        RouteDefinition route = routeCaptor.getValue();
        assertEquals("/customers", route.path());
        assertEquals("plugin/crm/page/customers", route.view());
        assertEquals("crm.customer.view", route.permission());
    }

    @Test
    void testBootRegistersAsset() {
        crmPluginBoot.boot(context);

        ArgumentCaptor<AssetDefinition> assetCaptor = ArgumentCaptor.forClass(AssetDefinition.class);
        verify(assetRegistry).register(assetCaptor.capture());

        AssetDefinition asset = assetCaptor.getValue();
        assertEquals("/", asset.requestPath());
        assertEquals("classpath:static/plugin/crm/", asset.resourceLocation());
        assertTrue(asset.cacheable());
    }
}
