package com.jnimble.plugin.printer.feie;

import com.jnimble.plugin.printer.spi.PrintResult;
import com.jnimble.plugin.printer.spi.PrintTask;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FeiePrinterDriver 单元测试类
 */
class FeiePrinterDriverTest {

    private FeiePrinterDriver driver;

    @BeforeEach
    void setUp() {
        driver = new FeiePrinterDriver();
    }

    @Test
    @DisplayName("驱动ID应返回feie")
    void driverIdShouldReturnFeie() {
        assertEquals("feie", driver.driverId());
    }

    @Test
    @DisplayName("驱动名称应返回飞鹅云打印")
    void driverNameShouldReturnFeieCloudPrint() {
        assertEquals("飞鹅云打印", driver.driverName());
    }

    @Test
    @DisplayName("支持飞鹅驱动配置时应返回true")
    void supportsShouldReturnTrueForFeieConfig() {
        PrinterConfig config = new PrinterConfig("feie", Map.of());
        assertTrue(driver.supports(config));
    }

    @Test
    @DisplayName("不支持其他驱动配置时应返回false")
    void supportsShouldReturnFalseForOtherConfig() {
        PrinterConfig config = new PrinterConfig("other", Map.of());
        assertFalse(driver.supports(config));
    }

    @Test
    @DisplayName("打印任务应返回成功结果")
    void printShouldReturnSuccessResult() {
        PrintTask task = new PrintTask("order-123", "test content", "text", 1);
        PrinterConfig config = new PrinterConfig("feie", Map.of());

        PrintResult result = driver.print(task, config);

        assertTrue(result.success());
        assertEquals("order-123", result.jobId());
        assertNotNull(result.message());
    }

    @Test
    @DisplayName("查询状态应返回在线状态")
    void queryStatusShouldReturnOnline() {
        PrinterConfig config = new PrinterConfig("feie", Map.of());
        PrinterStatus status = driver.queryStatus(config);
        assertEquals(PrinterStatus.ONLINE, status);
    }
}
