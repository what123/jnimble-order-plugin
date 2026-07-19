package com.jnimble.plugin.payment;

import com.jnimble.plugin.payment.model.enums.PaymentMethod;
import com.jnimble.sdk.hook.HookRegistry;
import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.plugin.PluginDescriptor;
import com.jnimble.sdk.resource.AssetDefinition;
import com.jnimble.sdk.resource.AssetRegistry;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteMethod;
import com.jnimble.sdk.route.RouteRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 支付插件启动类单元测试。
 * 测试插件启动时的路由注册、钩子注册和静态资源注册功能。
 */
@ExtendWith(MockitoExtension.class)
class PaymentPluginBootTest {

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

    private PaymentPluginBoot paymentPluginBoot;

    @BeforeEach
    void setUp() {
        paymentPluginBoot = new PaymentPluginBoot();
        lenient().when(context.hooks()).thenReturn(hookRegistry);
        lenient().when(context.routes()).thenReturn(routeRegistry);
        lenient().when(context.assets()).thenReturn(assetRegistry);
        lenient().when(context.descriptor()).thenReturn(descriptor);
        lenient().when(descriptor.id()).thenReturn("payment");
    }

    /**
     * 测试插件启动：验证支付记录路由注册的路径、视图和权限。
     */
    @Test
    void testBootRegistersRoute() {
        paymentPluginBoot.boot(context);

        ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
        verify(routeRegistry, times(3)).register(routeCaptor.capture());

        assertEquals(List.of("/records", "/diagnostics", "/diagnostics/{id}"),
                routeCaptor.getAllValues().stream().map(RouteDefinition::path).toList());
        RouteDefinition route = routeCaptor.getAllValues().getFirst();
        assertEquals(RouteMethod.GET, route.method());
        assertEquals("plugin/payment/admin/records", route.view());
        assertEquals("payment.view", route.permission());
    }

    /**
     * 测试插件启动：验证点餐侧边栏钩子注册被正确调用。
     */
    @Test
    void testBootRegistersSidebarHook() {
        paymentPluginBoot.boot(context);

        ArgumentCaptor<HookViewContribution> hookCaptor = ArgumentCaptor.forClass(HookViewContribution.class);
        verify(hookRegistry).register(eq("admin.layout.sidebar"), hookCaptor.capture());

        HookViewContribution contribution = hookCaptor.getValue();
        assertEquals("plugin/payment/fragment/sidebar", contribution.view());
        assertEquals(25, contribution.order());
        assertEquals("payment.view", contribution.permission());
        assertNull(contribution.activeWhen());
        assertEquals("payment", contribution.model().get("pluginId"));
    }

    /**
     * 测试插件启动：验证静态资源注册路径和缓存配置。
     */
    @Test
    void testBootRegistersAsset() {
        paymentPluginBoot.boot(context);

        ArgumentCaptor<AssetDefinition> assetCaptor = ArgumentCaptor.forClass(AssetDefinition.class);
        verify(assetRegistry).register(assetCaptor.capture());

        AssetDefinition asset = assetCaptor.getValue();
        assertEquals("/", asset.requestPath());
        assertEquals("classpath:static/plugin/payment/", asset.resourceLocation());
        assertTrue(asset.cacheable());
    }

    /**
     * 测试插件启动：验证各注册中心均被正确调用且次数匹配。
     */
    @Test
    void testBootCallsAllRegistries() {
        paymentPluginBoot.boot(context);

        verify(routeRegistry, times(3)).register(any(RouteDefinition.class));
        verify(hookRegistry, times(1)).register(anyString(), any(HookViewContribution.class));
        verify(assetRegistry, times(1)).register(any(AssetDefinition.class));
    }

    /**
     * 测试 PaymentMethod 枚举值数量和 valueOf 解析。
     */
    @Test
    void testPaymentMethodEnum() {
        assertEquals(1, PaymentMethod.values().length);
        assertSame(PaymentMethod.CASH, PaymentMethod.valueOf("CASH"));
    }
}
