package com.jnimble.plugin.order;

import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteMethod;
import java.util.Map;

public class OrderPluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        String pluginId = context.descriptor().id();

        context.routes().register(new RouteDefinition("/orders", RouteMethod.GET, "plugin/order-core/admin/orders", "order-core.admin.view"));
        context.routes().register(new RouteDefinition("/orders/{id}", RouteMethod.GET, "plugin/order-core/admin/order-detail", "order-core.admin.view"));
        context.routes().register(new RouteDefinition("/kitchen", RouteMethod.GET, "plugin/order-core/admin/kitchen-queue", "order-core.kitchen.view"));
        context.routes().register(new RouteDefinition("/kitchen/queue", RouteMethod.GET, null, "order-core.kitchen.view"));
        context.routes().register(new RouteDefinition("/kitchen/queue/{id}/start", RouteMethod.POST, null, "order-core.kitchen.operate"));
        context.routes().register(new RouteDefinition("/kitchen/queue/groups/start", RouteMethod.POST, null, "order-core.kitchen.operate"));
        context.routes().register(new RouteDefinition("/kitchen/queue/{id}/complete", RouteMethod.POST, null, "order-core.kitchen.operate"));
        context.routes().register(new RouteDefinition("/kitchen/queue/production-batches/complete", RouteMethod.POST, null, "order-core.kitchen.operate"));
        context.routes().register(new RouteDefinition("/kitchen/queue/orders/{orderId}/print", RouteMethod.POST, null, "order-core.kitchen.print"));

        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/order-core/fragment/sidebar-kitchen",
                        Map.of("pluginId", pluginId),
                        12,
                        "order-core.kitchen.view",
                        null
                )
        );

        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/order-core/fragment/sidebar-orders",
                        Map.of("pluginId", pluginId),
                        15,
                        "order-core.admin.view",
                        null
                )
        );
    }
}
