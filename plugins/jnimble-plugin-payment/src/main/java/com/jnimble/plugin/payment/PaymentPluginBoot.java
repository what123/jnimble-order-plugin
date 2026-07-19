package com.jnimble.plugin.payment;

import com.jnimble.sdk.hook.HookViewContribution;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;
import com.jnimble.sdk.resource.AssetDefinition;
import com.jnimble.sdk.route.RouteDefinition;
import com.jnimble.sdk.route.RouteMethod;
import com.jnimble.plugin.order.payment.OrderPaymentProviderRegistry;
import com.jnimble.plugin.payment.provider.LocalOrderPaymentProvider;
import java.util.Map;

public class PaymentPluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        context.findBean(OrderPaymentProviderRegistry.class).ifPresent(registry ->
                context.findBean(LocalOrderPaymentProvider.class).ifPresent(provider ->
                        context.registerHandle(registry.register(provider))
                )
        );

        context.routes().register(new RouteDefinition(
                "/records",
                RouteMethod.GET,
                "plugin/payment/admin/records",
                "payment.view"
        ));
        context.routes().register(new RouteDefinition(
                "/diagnostics",
                RouteMethod.GET,
                "plugin/payment/admin/diagnostics",
                "payment.view"
        ));
        context.routes().register(new RouteDefinition(
                "/diagnostics/{id}",
                RouteMethod.GET,
                "plugin/payment/admin/diagnostic-detail",
                "payment.view"
        ));

        context.hooks().register(
                "admin.layout.sidebar",
                new HookViewContribution(
                        "plugin/payment/fragment/sidebar",
                        Map.of("pluginId", context.descriptor().id()),
                        25,
                        "payment.view",
                        null
                )
        );

        context.assets().register(new AssetDefinition(
                "/",
                "classpath:static/plugin/payment/",
                true
        ));
    }
}
