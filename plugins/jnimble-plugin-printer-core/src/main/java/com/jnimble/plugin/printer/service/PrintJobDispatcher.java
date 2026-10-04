package com.jnimble.plugin.printer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnimble.plugin.printer.model.entity.PrintJobEntity;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import com.jnimble.plugin.printer.spi.PrintResult;
import com.jnimble.plugin.printer.spi.PrintTask;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterDriver;
import com.jnimble.plugin.printer.spi.PrinterDriverRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 打印任务调度器:周期性拉取 {@code PENDING} 任务,交给对应 driver 执行。
 *
 * <p>调度间隔通过 {@code jnimble.printer.dispatcher.interval-ms} 配置,默认 5 秒。
 * 每轮最多处理 {@code jnimble.printer.dispatcher.batch-size} 个任务,默认 20。</p>
 *
 * <p>失败任务根据 {@link PrintJobEntity#getRetryCount()} 与 maxRetries 决定是重试还是终止。</p>
 */
@Component
public class PrintJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PrintJobDispatcher.class);

    private final PrintJobService printJobService;
    private final PrinterService printerService;
    private final PrinterDriverRegistry driverRegistry;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final boolean enabled;

    public PrintJobDispatcher(
            PrintJobService printJobService,
            PrinterService printerService,
            PrinterDriverRegistry driverRegistry,
            ObjectMapper objectMapper,
            @Value("${jnimble.printer.dispatcher.batch-size:20}") int batchSize,
            @Value("${jnimble.printer.dispatcher.enabled:true}") boolean enabled
    ) {
        this.printJobService = printJobService;
        this.printerService = printerService;
        this.driverRegistry = driverRegistry;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${jnimble.printer.dispatcher.interval-ms:5000}")
    public void dispatch() {
        if (!enabled) {
            return;
        }
        List<PrintJobEntity> jobs = printJobService.listPendingJobs(batchSize);
        if (jobs.isEmpty()) {
            return;
        }
        for (PrintJobEntity job : jobs) {
            try {
                dispatchOne(job);
            } catch (Exception ex) {
                log.error("Dispatcher unexpected error on job {}", job.getId(), ex);
                printJobService.markFailed(job.getId(), "Dispatcher error: " + ex.getMessage());
            }
        }
    }

    private void dispatchOne(PrintJobEntity job) {
        PrinterEntity printer = printerService.getPrinter(job.getPrinterId());
        if (printer == null || !Boolean.TRUE.equals(printer.getEnabled())) {
            printJobService.markFailed(job.getId(), "Printer not available: " + job.getPrinterId());
            return;
        }
        PrinterDriver driver;
        try {
            driver = driverRegistry.resolve(printer.getDriverId());
        } catch (IllegalArgumentException ex) {
            printJobService.markFailed(job.getId(), "Driver not registered: " + printer.getDriverId());
            return;
        }
        PrinterConfig config = buildConfig(printer);
        if (!driver.supports(config)) {
            printJobService.markFailed(job.getId(), "Driver does not support printer config: " + printer.getDriverId());
            return;
        }
        PrintTask task = new PrintTask(
                job.getOrderId(),
                job.getContent(),
                job.getContentType(),
                job.getContentEncoding(),
                job.getType(),
                job.getRetryCount() == null ? 1 : Math.max(1, job.getRetryCount() + 1)
        );
        PrintResult result;
        try {
            result = driver.print(task, config);
        } catch (RuntimeException ex) {
            printJobService.markFailed(job.getId(), "Driver exception: " + ex.getMessage());
            return;
        }
        if (result.success()) {
            printJobService.markSuccess(job.getId(), result.jobId());
        } else {
            printJobService.markFailed(job.getId(), result.message());
        }
    }

    private PrinterConfig buildConfig(PrinterEntity printer) {
        Map<String, String> properties = new HashMap<>();
        String configJson = printer.getConfigJson();
        if (configJson != null && !configJson.isBlank()) {
            try {
                var parsed = objectMapper.readTree(configJson);
                parsed.fields().forEachRemaining(entry -> {
                    var value = entry.getValue();
                    if (value != null && !value.isNull()) {
                        properties.put(entry.getKey(), value.asText());
                    }
                });
            } catch (Exception ex) {
                log.warn("Failed to parse printer configJson for {}: {}", printer.getId(), ex.getMessage());
            }
        }
        return new PrinterConfig(printer.getDriverId(), properties);
    }
}
