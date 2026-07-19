package com.jnimble.plugin.menu;

import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.resource.AssetDefinition;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteMethod;
import java.util.Map;

public class MenuPluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        context.routes().register(new RouteDefinition(
                "/categories", RouteMethod.GET, "plugin/menu-manager/admin/categories", "menu-manager.admin.view"));
        context.routes().register(new RouteDefinition(
                "/items", RouteMethod.GET, "plugin/menu-manager/admin/items", "menu-manager.admin.view"));

        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/menu-manager/fragment/sidebar",
                        Map.of("pluginId", context.descriptor().id()),
                        20,
                        "menu-manager.admin.view",
                        null
                )
        );

        context.assets().register(new AssetDefinition(
                "/", "classpath:static/plugin/menu-manager/", true
        ));
    }
}
