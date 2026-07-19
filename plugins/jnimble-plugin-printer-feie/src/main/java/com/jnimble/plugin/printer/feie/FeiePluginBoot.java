package com.jnimble.plugin.printer.feie;

import com.jnimble.plugin.printer.spi.PrinterDriverRegistry;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;

public class FeiePluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        PrinterDriverRegistry registry = context.bean(PrinterDriverRegistry.class);
        context.registerHandle(registry.register(new FeiePrinterDriver()));
    }
}
