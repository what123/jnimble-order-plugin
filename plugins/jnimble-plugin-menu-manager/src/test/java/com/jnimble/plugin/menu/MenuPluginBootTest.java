package com.jnimble.plugin.menu;

import com.jnimble.sdk.hook.HookRegistry;
import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.plugin.PluginDescriptor;
import com.jnimble.sdk.resource.AssetDefinition;
import com.jnimble.sdk.resource.AssetRegistry;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 菜单管理插件启动类单元测试。
 * 测试插件启动时的路由注册、钩子注册和静态资源注册功能。
 */
@ExtendWith(MockitoExtension.class)
class MenuPluginBootTest {

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

    private MenuPluginBoot menuPluginBoot;

    @BeforeEach
    void setUp() {
        menuPluginBoot = new MenuPluginBoot();
        lenient().when(context.hooks()).thenReturn(hookRegistry);
        lenient().when(context.routes()).thenReturn(routeRegistry);
        lenient().when(context.assets()).thenReturn(assetRegistry);
        lenient().when(context.descriptor()).thenReturn(descriptor);
        lenient().when(descriptor.id()).thenReturn("menu-manager");
    }

    /**
     * 测试插件启动：验证注册了分类管理和菜品管理两个路由。
     */
    @Test
    void testBootRegistersRoutes() {
        menuPluginBoot.boot(context);

        ArgumentCaptor<RouteDefinition> routeCaptor = ArgumentCaptor.forClass(RouteDefinition.class);
        verify(routeRegistry, times(2)).register(routeCaptor.capture());

        List<RouteDefinition> routes = routeCaptor.getAllValues();

        // 验证分类路由
        RouteDefinition categoryRoute = routes.get(0);
        assertEquals("/categories", categoryRoute.path());
        assertEquals("plugin/menu-manager/admin/categories", categoryRoute.view());
        assertEquals("menu-manager.admin.view", categoryRoute.permission());

        // 验证菜品路由
        RouteDefinition itemRoute = routes.get(1);
        assertEquals("/items", itemRoute.path());
        assertEquals("plugin/menu-manager/admin/items", itemRoute.view());
        assertEquals("menu-manager.admin.view", itemRoute.permission());
    }

    /**
     * 测试插件启动：验证侧边栏钩子注册被正确调用。
     */
    @Test
    void testBootRegistersSidebarHook() {
        menuPluginBoot.boot(context);

        ArgumentCaptor<HookViewContribution> hookCaptor = ArgumentCaptor.forClass(HookViewContribution.class);
        verify(hookRegistry).register(eq("admin.layout.sidebar"), hookCaptor.capture());

        HookViewContribution contribution = hookCaptor.getValue();
        assertEquals("plugin/menu-manager/fragment/sidebar", contribution.view());
        assertEquals(20, contribution.order());
        assertEquals("menu-manager.admin.view", contribution.permission());
        assertNull(contribution.activeWhen());
        assertEquals("menu-manager", contribution.model().get("pluginId"));
    }

    /**
     * 测试插件启动：验证静态资源注册路径和缓存配置。
     */
    @Test
    void testBootRegistersAsset() {
        menuPluginBoot.boot(context);

        ArgumentCaptor<AssetDefinition> assetCaptor = ArgumentCaptor.forClass(AssetDefinition.class);
        verify(assetRegistry).register(assetCaptor.capture());

        AssetDefinition asset = assetCaptor.getValue();
        assertEquals("/", asset.requestPath());
        assertEquals("classpath:static/plugin/menu-manager/", asset.resourceLocation());
        assertTrue(asset.cacheable());
    }

    /**
     * 测试插件启动：验证各注册中心均被调用一次。
     */
    @Test
    void testBootCallsAllRegistriesExactlyOnce() {
        menuPluginBoot.boot(context);

        verify(routeRegistry, times(2)).register(any(RouteDefinition.class));
        verify(hookRegistry, times(1)).register(anyString(), any(HookViewContribution.class));
        verify(assetRegistry, times(1)).register(any(AssetDefinition.class));
    }
}
