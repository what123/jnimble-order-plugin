package com.jnimble.plugin.scan;

import com.jnimble.sdk.plugin.PluginBoot;
import com.jnimble.sdk.plugin.PluginContext;

public class ScanConsumerPluginBoot implements PluginBoot {

    @Override
    public void boot(PluginContext context) {
        // Consumer API routes are handled by Spring MVC controllers directly
        // at /api/consumer/** and /api/common/** — no admin route registration needed.
        // The security configuration permits all non-admin paths by default.
    }
}
