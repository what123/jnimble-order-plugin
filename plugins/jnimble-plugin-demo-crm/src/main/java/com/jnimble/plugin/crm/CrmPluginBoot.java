package com.jnimble.plugin.crm;

import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.resource.AssetDefinition;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteMethod;

import java.util.Map;

public class CrmPluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        String pluginId = context.descriptor().id();
        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/crm/fragment/sidebar",
                        Map.of("pluginId", pluginId),
                        40,
                        "crm.customer.view",
                        null
                )
        );
        context.routes().register(new RouteDefinition(
                "/customers",
                RouteMethod.GET,
                "plugin/crm/page/customers",
                "crm.customer.view"
        ));
        context.assets().register(new AssetDefinition(
                "/",
                "classpath:static/plugin/crm/",
                true
        ));
    }
}
