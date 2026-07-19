package com.jnimble.plugin.printer.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PrinterDriverRegistry 单元测试。
 *
 * <p>测试打印机驱动注册表的注册、解析和查询功能。</p>
 */
class PrinterDriverRegistryTest {

    private PrinterDriverRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PrinterDriverRegistry();
    }

    /**
     * 测试注册驱动后可以通过驱动ID正确解析。
     */
    @Test
    void registerShouldStoreDriver() {
        PrinterDriver driver = createMockDriver("epson-t1", "Epson T1");
        registry.register(driver);

        PrinterDriver resolved = registry.resolve("epson-t1");
        assertNotNull(resolved);
        assertEquals("epson-t1", resolved.driverId());
    }

    /**
     * 测试解析已注册的驱动返回正确的驱动名称。
     */
    @Test
    void resolveShouldReturnRegisteredDriver() {
        PrinterDriver driver = createMockDriver("hp-200", "HP 200");
        registry.register(driver);

        PrinterDriver resolved = registry.resolve("hp-200");
        assertEquals("HP 200", resolved.driverName());
    }

    /**
     * 测试解析不存在的驱动ID时抛出 IllegalArgumentException。
     */
    @Test
    void resolveShouldThrowWhenDriverNotFound() {
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("non-existent"));
    }

    /**
     * 测试获取所有驱动集合包含所有已注册的驱动。
     */
    @Test
    void allDriversShouldReturnAllRegisteredDrivers() {
        registry.register(createMockDriver("d1", "Driver 1"));
        registry.register(createMockDriver("d2", "Driver 2"));

        Collection<PrinterDriver> drivers = registry.allDrivers();
        assertEquals(2, drivers.size());
    }

    /**
     * 测试 allDrivers 返回的集合不可修改。
     */
    @Test
    void allDriversShouldReturnUnmodifiableCollection() {
        registry.register(createMockDriver("d1", "Driver 1"));
        Collection<PrinterDriver> drivers = registry.allDrivers();

        assertThrows(UnsupportedOperationException.class,
                () -> drivers.add(createMockDriver("d2", "Driver 2")));
    }

    /**
     * 测试重复注册相同ID的驱动会覆盖已有的驱动。
     */
    @Test
    void registerShouldOverwriteExistingDriver() {
        registry.register(createMockDriver("d1", "Original"));
        registry.register(createMockDriver("d1", "Updated"));

        PrinterDriver resolved = registry.resolve("d1");
        assertEquals("Updated", resolved.driverName());
    }

    private PrinterDriver createMockDriver(String id, String name) {
        PrinterDriver driver = mock(PrinterDriver.class);
        when(driver.driverId()).thenReturn(id);
        when(driver.driverName()).thenReturn(name);
        return driver;
    }
}
