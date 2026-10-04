package com.jnimble.plugin.printer.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.printer.model.dto.PrintFlowSaveRequest;
import com.jnimble.plugin.printer.model.dto.PrintTemplateSummary;
import com.jnimble.plugin.printer.model.entity.PrintJobEntity;
import com.jnimble.plugin.printer.model.entity.PrintNodeEntity;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import com.jnimble.plugin.printer.service.PrintFlowService;
import com.jnimble.plugin.printer.service.PrintJobService;
import com.jnimble.plugin.printer.service.PrintNodeService;
import com.jnimble.plugin.printer.service.PrintTemplateRenderService;
import com.jnimble.plugin.printer.service.PrintTemplateService;
import com.jnimble.plugin.printer.service.PrinterService;
import com.jnimble.plugin.printer.spi.PrinterDriverRegistry;
import com.jnimble.plugin.printer.template.PrintTemplateExtensionRegistry;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * PrinterAdminController 单元测试。
 *
 * <p>测试打印机管理控制器的各个端点，验证请求处理和响应返回。</p>
 */
class PrinterAdminControllerTest {

    private PrinterService printerService;
    private PrintNodeService printNodeService;
    private PrintFlowService printFlowService;
    private PrintJobService printJobService;
    private PrintTemplateService printTemplateService;
    private PrintTemplateRenderService printTemplateRenderService;
    private PrintTemplateExtensionRegistry printTemplateExtensionRegistry;
    private PrinterDriverRegistry driverRegistry;
    private ControllerAuthorization authorization;
    private PrinterAdminController controller;

    @BeforeEach
    void setUp() {
        printerService = mock(PrinterService.class);
        printNodeService = mock(PrintNodeService.class);
        printFlowService = mock(PrintFlowService.class);
        printJobService = mock(PrintJobService.class);
        printTemplateService = mock(PrintTemplateService.class);
        printTemplateRenderService = mock(PrintTemplateRenderService.class);
        printTemplateExtensionRegistry = mock(PrintTemplateExtensionRegistry.class);
        driverRegistry = mock(PrinterDriverRegistry.class);
        authorization = mock(ControllerAuthorization.class);
        controller = new PrinterAdminController(
                printerService,
                printNodeService,
                printFlowService,
                printJobService,
                printTemplateService,
                printTemplateRenderService,
                printTemplateExtensionRegistry,
                driverRegistry,
                authorization,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );
    }

    /**
     * 测试保存打印机时返回包含成功状态和ID的映射。
     */
    @Test
    void savePrinterShouldReturnSuccessResult() {
        PrinterEntity entity = new PrinterEntity();
        PrinterEntity created = new PrinterEntity();
        created.setId("p1");
        when(printerService.createPrinter(entity)).thenReturn(created);

        Map<String, Object> result = controller.savePrinter(entity);

        assertEquals(true, result.get("success"));
        assertEquals("p1", result.get("id"));
        verify(authorization).requirePermission("printer-core.config");
    }

    /**
     * 测试更新打印机时返回包含成功状态的映射。
     */
    @Test
    void updatePrinterShouldReturnSuccessResult() {
        PrinterEntity entity = new PrinterEntity();
        PrinterEntity updatedEntity = new PrinterEntity();
        updatedEntity.setId("p1");
        when(printerService.updatePrinter(any(PrinterEntity.class))).thenReturn(updatedEntity);

        Map<String, Object> result = controller.updatePrinter("p1", entity);

        assertEquals(true, result.get("success"));
        assertEquals("p1", entity.getId());
        verify(authorization).requirePermission("printer-core.config");
    }

    /**
     * 测试删除打印机时返回包含成功状态的映射。
     */
    @Test
    void deletePrinterShouldReturnSuccessResult() {
        doNothing().when(printerService).deletePrinter(eq("p1"));

        Map<String, Object> result = controller.deletePrinter("p1");

        assertEquals(true, result.get("success"));
        verify(printerService).deletePrinter("p1");
        verify(authorization).requirePermission("printer-core.config");
    }

    /**
     * 测试获取打印机页面时返回正确的视图名称。
     */
    @Test
    void printersPageShouldReturnCorrectView() {
        String view = controller.printersPage();
        assertEquals("plugin/printer-core/admin/printers", view);
    }

    /**
     * 测试打印机列表 JSON 接口。
     */
    @Test
    void listPrintersShouldReturnPrinters() {
        when(printerService.listPrinters()).thenReturn(List.of());
        List<PrinterEntity> result = controller.listPrinters();
        assertNotNull(result);
        verify(printerService).listPrinters();
    }

    /**
     * 测试驱动列表 JSON 接口。
     */
    @Test
    void listDriversShouldReturnDrivers() {
        when(driverRegistry.allDrivers()).thenReturn(List.of());
        Collection<?> result = controller.listDrivers();
        assertNotNull(result);
        verify(driverRegistry).allDrivers();
    }

    /**
     * 测试获取节点页面时返回正确的视图名称和模型数据。
     */
    @Test
    void nodesPageShouldReturnCorrectView() {
        when(printNodeService.listNodes()).thenReturn(List.of());
        when(printerService.listPrinters()).thenReturn(List.of());
        when(printFlowService.getBillingMode()).thenReturn("POSTPAID");
        when(printTemplateService.listTemplates()).thenReturn(List.of());
        Map<String, Object> model = new HashMap<>();

        String view = controller.nodesPage(model);

        assertEquals("plugin/printer-core/admin/nodes", view);
        assertNotNull(model.get("nodes"));
        assertNotNull(model.get("printers"));
        assertNotNull(model.get("templates"));
        assertEquals("POSTPAID", model.get("billingMode"));
    }

    @Test
    void getNodeFlowShouldReturnNoContentWhenFlowDoesNotExist() {
        when(printFlowService.getFlowXml()).thenReturn(null);

        ResponseEntity<String> response = controller.getNodeFlow();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void getNodeFlowShouldReturnSavedXml() {
        String flowXml = "<bpmn:definitions />";
        when(printFlowService.getFlowXml()).thenReturn(flowXml);

        ResponseEntity<String> response = controller.getNodeFlow();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(flowXml, response.getBody());
    }

    @Test
    void saveNodeFlowShouldRequirePermissionAndPersistFlow() {
        PrintFlowSaveRequest request = new PrintFlowSaveRequest("<bpmn:definitions />", List.of());

        Map<String, Object> result = controller.saveNodeFlow(request);

        assertEquals(true, result.get("success"));
        verify(authorization).requirePermission("printer-core.config");
        verify(printFlowService).saveFlow(request);
    }

    /**
     * 测试获取作业页面时返回正确的视图名称。
     */
    @Test
    void jobsPageShouldReturnCorrectView() {
        String view = controller.jobsPage();
        assertEquals("plugin/printer-core/admin/jobs", view);
    }

    @Test
    void templatesPageShouldReturnCorrectView() {
        assertEquals("plugin/printer-core/admin/templates", controller.templatesPage());
    }

    @Test
    void listTemplatesShouldReturnTemplateSummaries() {
        when(printTemplateService.listTemplates()).thenReturn(List.of());

        List<PrintTemplateSummary> result = controller.listTemplates();

        assertNotNull(result);
        verify(printTemplateService).listTemplates();
    }

    /**
     * 测试作业列表 JSON 接口。
     */
    @Test
    void listJobsShouldReturnJobs() {
        when(printJobService.listJobs(null, null, null)).thenReturn(List.of());
        List<PrintJobEntity> result = controller.listJobs(null);
        assertNotNull(result);
        verify(printJobService).listJobs(null, null, null);
    }

    /**
     * 测试获取驱动页面时返回正确的视图名称和模型数据。
     */
    @Test
    void driversPageShouldReturnCorrectView() {
        when(driverRegistry.allDrivers()).thenReturn(List.of());
        Map<String, Object> model = new HashMap<>();

        String view = controller.driversPage(model);

        assertEquals("plugin/printer-core/admin/drivers", view);
        assertNotNull(model.get("drivers"));
    }
}
