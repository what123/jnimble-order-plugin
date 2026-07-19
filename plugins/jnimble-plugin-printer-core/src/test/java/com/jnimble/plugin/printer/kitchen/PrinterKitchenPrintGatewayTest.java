package com.jnimble.plugin.printer.kitchen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.order.kitchen.KitchenPrintRequest;
import com.jnimble.plugin.order.model.entity.KitchenQueueItemEntity;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.printer.model.dto.PrintTemplatePreviewResponse;
import com.jnimble.plugin.printer.model.dto.PublishedPrintTemplate;
import com.jnimble.plugin.printer.model.entity.PrintJobEntity;
import com.jnimble.plugin.printer.model.entity.PrintNodeEntity;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import com.jnimble.plugin.printer.service.PrintJobService;
import com.jnimble.plugin.printer.service.PrintNodeService;
import com.jnimble.plugin.printer.service.PrintTemplateRenderService;
import com.jnimble.plugin.printer.service.PrintTemplateService;
import com.jnimble.plugin.printer.service.PrinterService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrinterKitchenPrintGatewayTest {

    @Mock
    private PrintNodeService printNodeService;
    @Mock
    private PrinterService printerService;
    @Mock
    private PrintTemplateService printTemplateService;
    @Mock
    private PrintTemplateRenderService renderService;
    @Mock
    private PrintJobService printJobService;

    private ObjectMapper objectMapper;
    private PrinterKitchenPrintGateway gateway;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        gateway = new PrinterKitchenPrintGateway(
                objectMapper,
                printNodeService,
                printerService,
                printTemplateService,
                renderService,
                printJobService
        );
    }

    @Test
    void resolvedTicketNumberIsIncludedInKitchenItemsAndPrintJob() {
        PrintNodeEntity node = new PrintNodeEntity();
        node.setNodeName("order.confirmed");
        node.setOperationMode("PRINT");
        node.setEnabled(true);
        node.setPrinterId("printer-1");
        node.setTemplateId("template-1");
        PrinterEntity printer = new PrinterEntity();
        printer.setId("printer-1");
        printer.setEnabled(true);
        printer.setDriverId("feie");
        PublishedPrintTemplate template = new PublishedPrintTemplate(
                "template-1", 3, 58, objectMapper.createObjectNode()
        );
        ObjectNode document = objectMapper.createObjectNode().put("schemaVersion", 1);
        PrintTemplatePreviewResponse rendered = new PrintTemplatePreviewResponse(document, "{}", List.of());
        PrintJobEntity job = new PrintJobEntity();
        job.setId("job-1");
        when(printNodeService.getNodeByName("order.confirmed")).thenReturn(node);
        when(printerService.getPrinter("printer-1")).thenReturn(printer);
        when(printTemplateService.getPublishedTemplate("template-1")).thenReturn(template);
        when(renderService.renderPublished(eq(template), any(JsonNode.class))).thenReturn(rendered);
        when(printJobService.createDocumentJob("10", node, printer, template, rendered)).thenReturn(job);

        var result = gateway.print(request());

        assertEquals("job-1", result.jobId());
        ArgumentCaptor<JsonNode> dataCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(renderService).renderPublished(eq(template), dataCaptor.capture());
        assertEquals("大堂1号", dataCaptor.getValue().path("kitchen").path("number").asText());
        assertEquals("大堂1号", dataCaptor.getValue().path("items").get(0).path("number").asText());
        assertEquals("宫保鸡丁", dataCaptor.getValue().path("items").get(0).path("itemName").asText());
    }

    private KitchenPrintRequest request() {
        OrderEntity order = new OrderEntity();
        order.setId(10L);
        order.setOrderNo("D001");
        order.setTableId(8L);
        order.setSource("TABLE");
        order.setStatus("CONFIRMED");
        order.setTotalAmount(new BigDecimal("28.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(new BigDecimal("28.00"));
        KitchenQueueItemEntity item = new KitchenQueueItemEntity();
        item.setItemName("宫保鸡丁");
        item.setSpecification("微辣");
        item.setRemark("少盐");
        item.setQuantity(1);
        return new KitchenPrintRequest(order, List.of(item), "大堂1号");
    }
}

