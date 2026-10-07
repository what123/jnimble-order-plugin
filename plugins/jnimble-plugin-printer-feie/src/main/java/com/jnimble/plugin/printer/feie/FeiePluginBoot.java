package com.jnimble.plugin.printer.feie;

import com.jnimble.plugin.printer.spi.PrinterDriverRegistry;
import com.jnimble.plugin.printer.template.FeieEscPosRenderer;
import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;

/**
 * 飞鹅云打印插件入口。
 *
 * <p>飞鹅账号(user/ukey)从系统属性 {@code jnimble.plugin.feie.user} / {@code jnimble.ukey}
 * 读取,默认空字符串(测试环境用)。生产环境通过 {@code -Djnimble.plugin.feie.user=xxx
 * -Djnimble.plugin.feie.ukey=xxx} 或环境变量注入。</p>
 *
 * <p>每台打印机的 {@code sn} 在打印机配置时填入 {@code PrinterConfig.properties().get("sn")}。</p>
 */
public class FeiePluginBoot implements PluginBoot {

    private static final String PROP_USER = "jnimble.plugin.feie.user";
    private static final String PROP_UKEY = "jnimble.plugin.feie.ukey";
    private static final String PROP_GATEWAY = "jnimble.plugin.feie.gateway";
    private static final String ENV_USER = "JNIMBLE_FEIE_USER";
    private static final String ENV_UKEY = "JNIMBLE_FEIE_UKEY";
    private static final String ENV_GATEWAY = "JNIMBLE_FEIE_GATEWAY";

    @Override
    public void boot(PluginContext context) {
        PrinterDriverRegistry registry = context.bean(PrinterDriverRegistry.class);
        FeieEscPosRenderer renderer = context.bean(FeieEscPosRenderer.class);

        String user = resolve(PROP_USER, ENV_USER, "");
        String ukey = resolve(PROP_UKEY, ENV_UKEY, "");
        String gateway = resolve(PROP_GATEWAY, ENV_GATEWAY, null);
        FeieApiClient client = gateway == null
                ? new FeieApiClient(user, ukey)
                : new FeieApiClient(user, ukey, gateway);

        context.registerHandle(registry.register(new FeiePrinterDriver(client, renderer)));
    }

    private String resolve(String systemProperty, String envVar, String fallback) {
        String value = System.getProperty(systemProperty);
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
