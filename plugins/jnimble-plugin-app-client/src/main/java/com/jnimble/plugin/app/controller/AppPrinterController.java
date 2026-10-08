package com.jnimble.plugin.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnimble.plugin.app.model.dto.ApiResult;
import com.jnimble.plugin.app.service.AppAuthService;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import com.jnimble.plugin.printer.service.PrinterService;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterDriver;
import com.jnimble.plugin.printer.spi.PrinterDriverRegistry;
import com.jnimble.plugin.printer.spi.PrinterOperationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/printers")
public class AppPrinterController {

    private static final String CONFIG_PERMISSION = "printer-core.config";

    private final PrinterService printerService;
    private final PrinterDriverRegistry driverRegistry;
    private final AppAuthService authService;
    private final ObjectMapper objectMapper;

    public AppPrinterController(PrinterService printerService,
                                PrinterDriverRegistry driverRegistry,
                                AppAuthService authService,
                                ObjectMapper objectMapper) {
        this.printerService = printerService;
        this.driverRegistry = driverRegistry;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Map<String, Object> listPrinters(HttpServletRequest request, HttpServletResponse response) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, CONFIG_PERMISSION);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("printers", printerService.listPrinters());
            return ApiResult.success(data);
        });
    }

    @GetMapping("/drivers")
    public Map<String, Object> listDrivers(HttpServletRequest request, HttpServletResponse response) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, CONFIG_PERMISSION);
            List<Map<String, String>> drivers = driverRegistry.allDrivers().stream()
                    .map(driver -> Map.of(
                            "driverId", driver.driverId(),
                            "driverName", driver.driverName()))
                    .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("drivers", drivers);
            return ApiResult.success(data);
        });
    }

    @PostMapping
    public Map<String, Object> createPrinter(HttpServletRequest request, HttpServletResponse response,
                                             @RequestBody PrinterEntity entity) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, CONFIG_PERMISSION);
            PrinterEntity created = printerService.createPrinter(entity);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", created.getId());
            return ApiResult.success(data);
        });
    }

    @PutMapping("/{id}")
    public Map<String, Object> updatePrinter(HttpServletRequest request, HttpServletResponse response,
                                             @PathVariable String id, @RequestBody PrinterEntity entity) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, CONFIG_PERMISSION);
            entity.setId(id);
            printerService.updatePrinter(entity);
            return ApiResult.success(new LinkedHashMap<>());
        });
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deletePrinter(HttpServletRequest request, HttpServletResponse response,
                                             @PathVariable String id) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, CONFIG_PERMISSION);
            printerService.deletePrinter(id);
            return ApiResult.success(new LinkedHashMap<>());
        });
    }

    @PostMapping("/{id}/clear-queue")
    public Map<String, Object> clearPrinterQueue(HttpServletRequest request, HttpServletResponse response,
                                                 @PathVariable String id) {
        return AppApiExecutor.guard(response, () -> {
            authService.requirePermission(request, CONFIG_PERMISSION);
            PrinterEntity printer = printerService.getPrinter(id);
            if (printer == null) {
                throw new IllegalArgumentException("打印机不存在：" + id);
            }
            PrinterDriver driver = driverRegistry.resolve(printer.getDriverId());
            PrinterOperationResult result = driver.clearPendingQueue(buildConfig(printer));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", result.success());
            data.put("driverId", result.driverId());
            data.put("message", result.message());
            return ApiResult.success(data);
        });
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
            } catch (Exception ignored) {
                properties.clear();
            }
        }
        return new PrinterConfig(printer.getDriverId(), properties);
    }
}
