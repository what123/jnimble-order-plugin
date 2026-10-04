package com.jnimble.plugin.printer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnimble.plugin.printer.model.entity.PrintJobEntity;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import com.jnimble.plugin.printer.spi.PrintResult;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterDriver;
import com.jnimble.plugin.printer.spi.PrinterDriverRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrintJobDispatcherTest {

    @Mock
    private PrintJobService printJobService;
    @Mock
    private PrinterService printerService;
    @Mock
    private PrinterDriverRegistry driverRegistry;
    @Mock
    private PrinterDriver driver;

    private PrintJobDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new PrintJobDispatcher(
                printJobService, printerService, driverRegistry, new ObjectMapper(),
                10, true);
    }

    @Test
    void shouldSkipWhenNoPendingJobs() {
        when(printJobService.listPendingJobs(anyInt())).thenReturn(List.of());
        dispatcher.dispatch();
        verifyNoInteractions(printerService, driverRegistry);
    }

    @Test
    void shouldSkipWhenDispatcherDisabled() {
        PrintJobDispatcher disabled = new PrintJobDispatcher(
                printJobService, printerService, driverRegistry, new ObjectMapper(),
                10, false);
        disabled.dispatch();
        verifyNoInteractions(printJobService);
    }

    @Test
    void shouldMarkFailedWhenPrinterMissing() {
        PrintJobEntity job = pendingJob("job-1", "printer-1");
        when(printJobService.listPendingJobs(anyInt())).thenReturn(List.of(job));
        when(printerService.getPrinter("printer-1")).thenReturn(null);

        dispatcher.dispatch();

        verify(printJobService).markFailed(eq("job-1"), contains("Printer not available"));
    }

    @Test
    void shouldMarkFailedWhenPrinterDisabled() {
        PrintJobEntity job = pendingJob("job-1", "printer-1");
        PrinterEntity printer = printer("printer-1", "feie");
        printer.setEnabled(false);
        when(printJobService.listPendingJobs(anyInt())).thenReturn(List.of(job));
        when(printerService.getPrinter("printer-1")).thenReturn(printer);

        dispatcher.dispatch();

        verify(printJobService).markFailed(eq("job-1"), contains("Printer not available"));
    }

    @Test
    void shouldMarkFailedWhenDriverNotRegistered() {
        PrintJobEntity job = pendingJob("job-1", "printer-1");
        PrinterEntity printer = printer("printer-1", "unknown-driver");
        when(printJobService.listPendingJobs(anyInt())).thenReturn(List.of(job));
        when(printerService.getPrinter("printer-1")).thenReturn(printer);
        when(driverRegistry.resolve("unknown-driver"))
                .thenThrow(new IllegalArgumentException("No driver found: unknown-driver"));

        dispatcher.dispatch();

        verify(printJobService).markFailed(eq("job-1"), contains("Driver not registered"));
    }

    @Test
    void shouldMarkFailedWhenDriverDoesNotSupportConfig() {
        PrintJobEntity job = pendingJob("job-1", "printer-1");
        PrinterEntity printer = printer("printer-1", "feie");
        when(printJobService.listPendingJobs(anyInt())).thenReturn(List.of(job));
        when(printerService.getPrinter("printer-1")).thenReturn(printer);
        when(driverRegistry.resolve("feie")).thenReturn(driver);
        when(driver.supports(any(PrinterConfig.class))).thenReturn(false);

        dispatcher.dispatch();

        verify(printJobService).markFailed(eq("job-1"), contains("Driver does not support"));
    }

    @Test
    void shouldMarkSuccessWhenDriverReturnsSuccess() {
        PrintJobEntity job = pendingJob("job-1", "printer-1");
        PrinterEntity printer = printer("printer-1", "feie");
        when(printJobService.listPendingJobs(anyInt())).thenReturn(List.of(job));
        when(printerService.getPrinter("printer-1")).thenReturn(printer);
        when(driverRegistry.resolve("feie")).thenReturn(driver);
        when(driver.supports(any(PrinterConfig.class))).thenReturn(true);
        when(driver.print(any(), any())).thenReturn(new PrintResult(true, "ext-1", "ok"));

        dispatcher.dispatch();

        verify(printJobService).markSuccess(eq("job-1"), eq("ext-1"));
    }

    @Test
    void shouldMarkFailedWhenDriverReturnsFailure() {
        PrintJobEntity job = pendingJob("job-1", "printer-1");
        PrinterEntity printer = printer("printer-1", "feie");
        when(printJobService.listPendingJobs(anyInt())).thenReturn(List.of(job));
        when(printerService.getPrinter("printer-1")).thenReturn(printer);
        when(driverRegistry.resolve("feie")).thenReturn(driver);
        when(driver.supports(any(PrinterConfig.class))).thenReturn(true);
        when(driver.print(any(), any())).thenReturn(new PrintResult(false, "job-1", "timeout"));

        dispatcher.dispatch();

        verify(printJobService).markFailed(eq("job-1"), eq("timeout"));
    }

    @Test
    void shouldMarkFailedWhenDriverThrows() {
        PrintJobEntity job = pendingJob("job-1", "printer-1");
        PrinterEntity printer = printer("printer-1", "feie");
        when(printJobService.listPendingJobs(anyInt())).thenReturn(List.of(job));
        when(printerService.getPrinter("printer-1")).thenReturn(printer);
        when(driverRegistry.resolve("feie")).thenReturn(driver);
        when(driver.supports(any(PrinterConfig.class))).thenReturn(true);
        when(driver.print(any(), any())).thenThrow(new RuntimeException("NPE"));

        dispatcher.dispatch();

        verify(printJobService).markFailed(eq("job-1"), contains("Driver exception"));
    }

    @Test
    void shouldPassThroughPrinterConfigJsonToDriverConfig() {
        PrintJobEntity job = pendingJob("job-1", "printer-1");
        PrinterEntity printer = printer("printer-1", "feie");
        printer.setConfigJson("{\"user\":\"u1\",\"ukey\":\"k1\",\"sn\":\"s1\"}");
        when(printJobService.listPendingJobs(anyInt())).thenReturn(List.of(job));
        when(printerService.getPrinter("printer-1")).thenReturn(printer);
        when(driverRegistry.resolve("feie")).thenReturn(driver);
        when(driver.supports(any(PrinterConfig.class))).thenReturn(true);
        when(driver.print(any(), any())).thenReturn(new PrintResult(true, "ext", "ok"));

        dispatcher.dispatch();

        ArgumentCaptor<PrinterConfig> configCaptor = ArgumentCaptor.forClass(PrinterConfig.class);
        verify(driver).print(any(), configCaptor.capture());
        PrinterConfig config = configCaptor.getValue();
        assertEquals("feie", config.driverId());
        assertEquals("u1", config.properties().get("user"));
        assertEquals("k1", config.properties().get("ukey"));
        assertEquals("s1", config.properties().get("sn"));
    }

    private PrintJobEntity pendingJob(String id, String printerId) {
        PrintJobEntity entity = new PrintJobEntity();
        entity.setId(id);
        entity.setOrderId("order-" + id);
        entity.setPrinterId(printerId);
        entity.setDriverId("feie");
        entity.setContent("{}");
        entity.setContentType("application/vnd.jnimble.print-document+json; charset=UTF-8");
        entity.setContentEncoding("PLAIN");
        entity.setType("KITCHEN");
        entity.setStatus("PENDING");
        entity.setRetryCount(0);
        entity.setMaxRetries(3);
        return entity;
    }

    private PrinterEntity printer(String id, String driverId) {
        PrinterEntity entity = new PrinterEntity();
        entity.setId(id);
        entity.setDriverId(driverId);
        entity.setEnabled(Boolean.TRUE);
        return entity;
    }
}
