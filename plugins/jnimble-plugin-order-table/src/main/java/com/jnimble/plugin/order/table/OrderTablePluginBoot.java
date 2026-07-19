package com.jnimble.plugin.order.table;

import com.jnimble.plugin.order.kitchen.KitchenTicketNumberRegistry;
import com.jnimble.plugin.order.table.kitchen.TableKitchenTicketNumberHook;
import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.resource.AssetDefinition;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteMethod;
import java.util.Map;

public class OrderTablePluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        String pluginId = context.descriptor().id();

        context.routes().register(new RouteDefinition("/pos", RouteMethod.GET,
                "plugin/order-table/pos/layout", "order-table.pos.view"));
        context.routes().register(new RouteDefinition("/pos/board", RouteMethod.GET,
                null, "order-table.pos.view"));
        context.routes().register(new RouteDefinition("/pos/catalog", RouteMethod.GET,
                null, "order-table.pos.view"));
        context.routes().register(new RouteDefinition("/pos/confirmations", RouteMethod.GET,
                null, "order-table.pos.view"));
        context.routes().register(new RouteDefinition(
                "/pos/confirmations/{submissionId}/confirm", RouteMethod.POST,
                null, "order-table.pos.confirm"));
        context.routes().register(new RouteDefinition(
                "/pos/confirmations/{submissionId}/return", RouteMethod.POST,
                null, "order-table.pos.confirm"));
        context.routes().register(new RouteDefinition("/pos/sessions/{sessionId}", RouteMethod.GET,
                null, "order-table.pos.view"));
        context.routes().register(new RouteDefinition("/pos/tables/{tableId}/open", RouteMethod.POST,
                null, "order-table.pos.open"));
        context.routes().register(new RouteDefinition("/pos/tables/{tableId}/share", RouteMethod.POST,
                null, "order-table.pos.open"));
        context.routes().register(new RouteDefinition("/pos/sessions/{sessionId}/items", RouteMethod.POST,
                null, "order-table.pos.view"));
        context.routes().register(new RouteDefinition(
                "/pos/sessions/{sessionId}/items/{itemId}/quantity", RouteMethod.POST,
                null, "order-table.pos.view"));
        context.routes().register(new RouteDefinition(
                "/pos/sessions/{sessionId}/items/{itemId}", RouteMethod.DELETE,
                null, "order-table.pos.view"));
        context.routes().register(new RouteDefinition("/pos/sessions/{sessionId}/confirm", RouteMethod.POST,
                null, "order-table.pos.view"));
        context.routes().register(new RouteDefinition(
                "/pos/sessions/{sessionId}/guest-count", RouteMethod.POST,
                null, "order-table.pos.open"));
        context.routes().register(new RouteDefinition("/pos/sessions/{sessionId}/combine", RouteMethod.POST,
                null, "order-table.pos.open"));
        context.routes().register(new RouteDefinition("/pos/sessions/{sessionId}/transfer", RouteMethod.POST,
                null, "order-table.pos.open"));
        context.routes().register(new RouteDefinition("/pos/sessions/{sessionId}/checkout", RouteMethod.POST,
                null, "order-table.pos.settle"));
        context.routes().register(new RouteDefinition(
                "/pos/tables/{tableId}/cleaning/complete", RouteMethod.POST,
                null, "order-table.pos.open"));
        context.routes().register(new RouteDefinition("/pos/orders/{orderId}/settle", RouteMethod.POST,
                null, "order-table.pos.settle"));
        context.routes().register(new RouteDefinition("/pos/settle-panel", RouteMethod.GET,
                null, "order-table.pos.view"));
        context.routes().register(new RouteDefinition("/tables", RouteMethod.GET,
                "plugin/order-table/admin/tables", "order-table.admin.view"));

        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/order-table/fragment/sidebar-pos",
                        Map.of("pluginId", pluginId),
                        5,
                        "order-table.pos.view",
                        null
                )
        );

        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/order-table/fragment/sidebar-tables",
                        Map.of("pluginId", pluginId),
                        22,
                        "order-table.admin.view",
                        null
                )
        );

        context.hooks().register(
                "pos.order.settle.panel",
                new HookViewContribution(
                        "plugin/order-table/pos/settle-panel",
                        Map.of(),
                        1000,
                        null,
                        null
                )
        );

        context.hooks().register(
                "pos.order.topbar",
                new HookViewContribution(
                        "plugin/order-table/pos/topbar-hook",
                        Map.of(),
                        1000,
                        null,
                        null
                )
        );

        context.assets().register(new AssetDefinition("/", "classpath:static/plugin/order-table/", true));

        context.findBean(KitchenTicketNumberRegistry.class).ifPresent(registry ->
                context.findBean(TableKitchenTicketNumberHook.class).ifPresent(hook ->
                        context.registerHandle(registry.register(pluginId, hook, 100))
                )
        );
    }
}
