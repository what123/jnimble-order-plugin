package com.jnimble.order;

import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteMethod;

public class OrderPluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/order-plugin/fragment/sidebar",
                        null,
                        100,
                        "order-plugin.view",
                        null
                )
        );

        context.routes().register(new RouteDefinition(
                "/index",
                RouteMethod.GET,
                "plugin/order-plugin/page/index",
                "order-plugin.view"
        ));
    }
}
