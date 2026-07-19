package com.jnimble.plugin.printer.feie;

import com.jnimble.plugin.printer.spi.PrinterDriver;
import com.jnimble.plugin.printer.spi.PrinterDriverRegistry;
import com.jnimble.sdk.plugin.PluginContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FeiePluginBoot 单元测试类
 */
@ExtendWith(MockitoExtension.class)
class FeiePluginBootTest {

    @Mock
    private PluginContext context;

    @Mock
    private PrinterDriverRegistry registry;

    private FeiePluginBoot pluginBoot;

    @BeforeEach
    void setUp() {
        pluginBoot = new FeiePluginBoot();
    }

    @Test
    @DisplayName("启动时应注册飞鹅打印驱动")
    void bootShouldRegisterFeiePrinterDriver() {
        when(context.bean(PrinterDriverRegistry.class)).thenReturn(registry);

        pluginBoot.boot(context);

        verify(registry).register(any(PrinterDriver.class));
    }

    @Test
    @DisplayName("启动时应从上下文获取注册表")
    void bootShouldGetRegistryFromContext() {
        when(context.bean(PrinterDriverRegistry.class)).thenReturn(registry);

        pluginBoot.boot(context);

        verify(context).bean(PrinterDriverRegistry.class);
    }

    @Test
    @DisplayName("启动时注册的驱动应为FeiePrinterDriver类型")
    void bootShouldRegisterFeiePrinterDriverType() {
        when(context.bean(PrinterDriverRegistry.class)).thenReturn(registry);

        pluginBoot.boot(context);

        verify(registry).register(argThat(driver -> driver instanceof FeiePrinterDriver));
    }

    @Test
    @DisplayName("启动时注册的驱动ID应为feie")
    void bootShouldRegisterDriverWithFeieId() {
        when(context.bean(PrinterDriverRegistry.class)).thenReturn(registry);

        pluginBoot.boot(context);

        verify(registry).register(argThat(driver -> "feie".equals(driver.driverId())));
    }
}
