package com.jnimble.plugin.printer;

import com.jnimble.plugin.order.kitchen.KitchenDispatchModeRegistry;
import com.jnimble.plugin.order.kitchen.KitchenPrintGatewayRegistry;
import com.jnimble.plugin.printer.kitchen.PrinterKitchenDispatchModeHook;
import com.jnimble.plugin.printer.kitchen.PrinterKitchenPrintGateway;
import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.resource.AssetDefinition;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteMethod;
import java.util.Map;

public class PrinterCorePluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        String pluginId = context.descriptor().id();

        context.routes().register(new RouteDefinition("/printers", RouteMethod.GET, "plugin/printer-core/admin/printers", "printer-core.config"));
        context.routes().register(new RouteDefinition("/printers/add", RouteMethod.GET, "plugin/printer-core/admin/printer-form", "printer-core.config"));
        context.routes().register(new RouteDefinition("/nodes", RouteMethod.GET, "plugin/printer-core/admin/nodes", "printer-core.config"));
        context.routes().register(new RouteDefinition("/templates", RouteMethod.GET, "plugin/printer-core/admin/templates", "printer-core.config"));
        context.routes().register(new RouteDefinition("/templates/{id}/edit", RouteMethod.GET, "plugin/printer-core/admin/template-editor", "printer-core.config"));
        context.routes().register(new RouteDefinition("/jobs", RouteMethod.GET, "plugin/printer-core/admin/jobs", "printer-core.view"));
        context.routes().register(new RouteDefinition("/drivers", RouteMethod.GET, "plugin/printer-core/admin/drivers", "printer-core.driver"));

        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/printer-core/fragment/sidebar",
                        Map.of("pluginId", pluginId),
                        30,
                        "printer-core.config",
                        null
                )
        );

        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/printer-core/fragment/sidebar-config",
                        Map.of("pluginId", pluginId),
                        31,
                        "printer-core.config",
                        null
                )
        );

        context.assets().register(new AssetDefinition(
                "/",
                "classpath:static/plugin/printer-core/",
                true
        ));

        context.findBean(KitchenDispatchModeRegistry.class).ifPresent(registry ->
                context.findBean(PrinterKitchenDispatchModeHook.class).ifPresent(hook ->
                        context.registerHandle(registry.register(pluginId, hook, 100))
                )
        );
        context.findBean(KitchenPrintGatewayRegistry.class).ifPresent(registry ->
                context.findBean(PrinterKitchenPrintGateway.class).ifPresent(gateway ->
                        context.registerHandle(registry.register(pluginId, gateway))
                )
        );
    }
}
