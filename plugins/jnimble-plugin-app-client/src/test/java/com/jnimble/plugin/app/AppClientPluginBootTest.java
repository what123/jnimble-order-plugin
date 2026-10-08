package com.jnimble.plugin.app;

import com.jnimble.sdk.plugin.PluginContext;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AppClientPluginBootTest {

    @Test
    void bootsWithoutRegisteringAdminUi() {
        AppClientPluginBoot boot = new AppClientPluginBoot();
        PluginContext context = mock(PluginContext.class);

        boot.boot(context);
        boot.stop(context);

        verifyNoInteractions(context);
    }
}
