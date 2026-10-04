package com.jnimble.plugin.printer.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.printer.model.dto.PrintFlowSaveRequest;
import com.jnimble.plugin.printer.model.dto.PrintTemplateDetail;
import com.jnimble.plugin.printer.model.dto.PrintTemplatePreviewRequest;
import com.jnimble.plugin.printer.model.dto.PrintTemplatePreviewResponse;
import com.jnimble.plugin.printer.model.dto.PrintTemplateSaveRequest;
import com.jnimble.plugin.printer.model.dto.PrintTemplateSummary;
import com.jnimble.plugin.printer.model.entity.PrintJobEntity;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import com.jnimble.plugin.printer.service.PrintFlowService;
import com.jnimble.plugin.printer.service.PrintJobService;
import com.jnimble.plugin.printer.service.PrintNodeService;
import com.jnimble.plugin.printer.service.PrintTemplateConflictException;
import com.jnimble.plugin.printer.service.PrintTemplateRenderService;
import com.jnimble.plugin.printer.service.PrintTemplateService;
import com.jnimble.plugin.printer.service.PrintTemplateValidationException;
import com.jnimble.plugin.printer.service.PrinterService;
import com.jnimble.plugin.printer.spi.PrinterOperationResult;
import com.jnimble.plugin.printer.spi.PrinterOrderStatistics;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterDriver;
import com.jnimble.plugin.printer.spi.PrinterDriverRegistry;
import com.jnimble.plugin.printer.template.PrintBlockDescriptor;
import com.jnimble.plugin.printer.template.PrintTemplateExtensionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Controller
@RequestMapping("/admin/plugins/printer-core")
public class PrinterAdminController {

    private final PrinterService printerService;
    private final PrintNodeService printNodeService;
    private final PrintFlowService printFlowService;
    private final PrintJobService printJobService;
    private final PrintTemplateService printTemplateService;
    private final PrintTemplateRenderService printTemplateRenderService;
    private final PrintTemplateExtensionRegistry printTemplateExtensionRegistry;
    private final PrinterDriverRegistry driverRegistry;
    private final ControllerAuthorization authorization;
    private final ObjectMapper objectMapper;

    public PrinterAdminController(PrinterService printerService,
                                  PrintNodeService printNodeService,
                                  PrintFlowService printFlowService,
                                  PrintJobService printJobService,
                                  PrintTemplateService printTemplateService,
                                  PrintTemplateRenderService printTemplateRenderService,
                                  PrintTemplateExtensionRegistry printTemplateExtensionRegistry,
                                  PrinterDriverRegistry driverRegistry,
                                  ControllerAuthorization authorization,
                                  ObjectMapper objectMapper) {
        this.printerService = printerService;
        this.printNodeService = printNodeService;
        this.printFlowService = printFlowService;
        this.printJobService = printJobService;
        this.printTemplateService = printTemplateService;
        this.printTemplateRenderService = printTemplateRenderService;
        this.printTemplateExtensionRegistry = printTemplateExtensionRegistry;
        this.driverRegistry = driverRegistry;
        this.authorization = authorization;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/printers")
    public String printersPage() {
        return "plugin/printer-core/admin/printers";
    }

    @GetMapping("/printers/list")
    @ResponseBody
    public List<PrinterEntity> listPrinters() {
        return printerService.listPrinters();
    }

    @GetMapping("/printers/add")
    public String addPrinterPage(Map<String, Object> model) {
        model.put("drivers", driverRegistry.allDrivers());
        return "plugin/printer-core/admin/printer-form";
    }

    @PostMapping("/printers")
    @ResponseBody
    public Map<String, Object> savePrinter(@RequestBody PrinterEntity entity) {
        authorization.requirePermission("printer-core.config");
        PrinterEntity created = printerService.createPrinter(entity);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("id", created.getId());
        return result;
    }

    @PutMapping("/printers/{id}")
    @ResponseBody
    public Map<String, Object> updatePrinter(@PathVariable String id, @RequestBody PrinterEntity entity) {
        authorization.requirePermission("printer-core.config");
        entity.setId(id);
        printerService.updatePrinter(entity);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @DeleteMapping("/printers/{id}")
    @ResponseBody
    public Map<String, Object> deletePrinter(@PathVariable String id) {
        authorization.requirePermission("printer-core.config");
        printerService.deletePrinter(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @GetMapping("/printers/drivers")
    @ResponseBody
    public Collection<?> listDrivers() {
        return driverRegistry.allDrivers();
    }

    /**
     * 清空指定打印机的远程待打印队列(云端未下发的任务)。
     * 通过 driver SPI 转发到具体云平台。
     */
    @PostMapping("/printers/{id}/clear-queue")
    @ResponseBody
    public Map<String, Object> clearPrinterQueue(@PathVariable String id) {
        authorization.requirePermission("printer-core.config");
        PrinterEntity printer = printerService.getPrinter(id);
        if (printer == null) {
            throw new IllegalArgumentException("Printer not found: " + id);
        }
        PrinterDriver driver = driverRegistry.resolve(printer.getDriverId());
        PrinterConfig config = buildConfig(printer);
        PrinterOperationResult result = driver.clearPendingQueue(config);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("driverId", result.driverId());
        response.put("message", result.message());
        return response;
    }

    /**
     * 查询指定打印机某天的订单统计。
     * 通过 driver SPI 转发到具体云平台。
     */
    @GetMapping("/printers/{id}/order-statistics")
    @ResponseBody
    public Map<String, Object> printerOrderStatistics(
            @PathVariable String id,
            @RequestParam String date
    ) {
        authorization.requirePermission("printer-core.view");
        PrinterEntity printer = printerService.getPrinter(id);
        if (printer == null) {
            throw new IllegalArgumentException("Printer not found: " + id);
        }
        PrinterDriver driver = driverRegistry.resolve(printer.getDriverId());
        PrinterConfig config = buildConfig(printer);
        PrinterOrderStatistics statistics = driver.queryOrderStatistics(config, date);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("supported", statistics.supported());
        response.put("printed", statistics.printed());
        response.put("waiting", statistics.waiting());
        response.put("message", statistics.message());
        return response;
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
                // 解析失败留空
            }
        }
        return new PrinterConfig(printer.getDriverId(), properties);
    }

    @GetMapping("/nodes")
    public String nodesPage(Map<String, Object> model) {
        model.put("nodes", printNodeService.listNodes());
        model.put("printers", printerService.listPrinters());
        model.put("templates", printTemplateService.listTemplates());
        model.put("billingMode", printFlowService.getBillingMode());
        return "plugin/printer-core/admin/nodes";
    }

    @GetMapping(value = "/nodes/flow", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public ResponseEntity<String> getNodeFlow() {
        String flowXml = printFlowService.getFlowXml();
        if (flowXml == null || flowXml.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(flowXml);
    }

    @PostMapping("/nodes/flow")
    @ResponseBody
    public Map<String, Object> saveNodeFlow(@RequestBody PrintFlowSaveRequest request) {
        authorization.requirePermission("printer-core.config");
        printFlowService.saveFlow(request);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @GetMapping("/jobs")
    public String jobsPage() {
        return "plugin/printer-core/admin/jobs";
    }

    @GetMapping("/jobs/list")
    @ResponseBody
    public List<PrintJobEntity> listJobs(@RequestParam(required = false) String status) {
        return printJobService.listJobs(status, null, null);
    }

    @PostMapping("/jobs/{id}/retry")
    @ResponseBody
    public Map<String, Object> retryJob(@PathVariable String id) {
        printJobService.retryJob(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @GetMapping("/templates")
    public String templatesPage() {
        return "plugin/printer-core/admin/templates";
    }

    @GetMapping("/templates/{id}/edit")
    public String templateEditorPage(@PathVariable String id, Map<String, Object> model) {
        model.put("templateId", id);
        return "plugin/printer-core/admin/template-editor";
    }

    @GetMapping("/templates/list")
    @ResponseBody
    public List<PrintTemplateSummary> listTemplates() {
        return printTemplateService.listTemplates();
    }

    @GetMapping("/templates/{id}/definition")
    @ResponseBody
    public PrintTemplateDetail getTemplate(@PathVariable String id) {
        return printTemplateService.getTemplate(id);
    }

    @GetMapping("/template-blocks")
    @ResponseBody
    public List<PrintBlockDescriptor> listTemplateBlocks() {
        return printTemplateExtensionRegistry.descriptors();
    }

    @PostMapping("/templates")
    @ResponseBody
    public PrintTemplateDetail createTemplate(@RequestBody PrintTemplateSaveRequest request) {
        authorization.requirePermission("printer-core.config");
        return printTemplateService.createTemplate(request);
    }

    @PutMapping("/templates/{id}/draft")
    @ResponseBody
    public PrintTemplateDetail saveTemplateDraft(
            @PathVariable String id,
            @RequestBody PrintTemplateSaveRequest request
    ) {
        authorization.requirePermission("printer-core.config");
        return printTemplateService.saveDraft(id, request);
    }

    @PostMapping("/templates/{id}/publish")
    @ResponseBody
    public PrintTemplateDetail publishTemplate(@PathVariable String id) {
        authorization.requirePermission("printer-core.config");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String publisher = authentication == null ? null : authentication.getName();
        return printTemplateService.publish(id, publisher);
    }

    @PostMapping("/templates/preview")
    @ResponseBody
    public PrintTemplatePreviewResponse previewTemplate(@RequestBody PrintTemplatePreviewRequest request) {
        authorization.requirePermission("printer-core.config");
        return printTemplateRenderService.preview(request);
    }

    @GetMapping("/drivers")
    public String driversPage(Map<String, Object> model) {
        model.put("drivers", driverRegistry.allDrivers());
        return "plugin/printer-core/admin/drivers";
    }

    @ExceptionHandler(PrintTemplateValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public Map<String, Object> handleTemplateValidation(PrintTemplateValidationException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", "PRINT_TEMPLATE_INVALID");
        result.put("message", "模板校验失败");
        result.put("violations", exception.getViolations());
        return result;
    }

    @ExceptionHandler(PrintTemplateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @ResponseBody
    public Map<String, Object> handleTemplateConflict(PrintTemplateConflictException exception) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", "PRINT_TEMPLATE_CONFLICT");
        result.put("message", exception.getMessage());
        return result;
    }
}
