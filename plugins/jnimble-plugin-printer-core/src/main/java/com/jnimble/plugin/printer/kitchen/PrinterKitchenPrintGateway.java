package com.jnimble.plugin.printer.kitchen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.order.kitchen.KitchenPrintGateway;
import com.jnimble.plugin.order.kitchen.KitchenPrintRequest;
import com.jnimble.plugin.order.kitchen.KitchenPrintResult;
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
import org.springframework.stereotype.Component;

@Component
public class PrinterKitchenPrintGateway implements KitchenPrintGateway {

    private final ObjectMapper objectMapper;
    private final PrintNodeService printNodeService;
    private final PrinterService printerService;
    private final PrintTemplateService printTemplateService;
    private final PrintTemplateRenderService renderService;
    private final PrintJobService printJobService;

    public PrinterKitchenPrintGateway(
            ObjectMapper objectMapper,
            PrintNodeService printNodeService,
            PrinterService printerService,
            PrintTemplateService printTemplateService,
            PrintTemplateRenderService renderService,
            PrintJobService printJobService
    ) {
        this.objectMapper = objectMapper;
        this.printNodeService = printNodeService;
        this.printerService = printerService;
        this.printTemplateService = printTemplateService;
        this.renderService = renderService;
        this.printJobService = printJobService;
    }

    @Override
    public KitchenPrintResult print(KitchenPrintRequest request) {
        PrintNodeEntity node = requirePrintNode();
        PrinterEntity printer = requirePrinter(node.getPrinterId());
        PublishedPrintTemplate template = printTemplateService.getPublishedTemplate(node.getTemplateId());
        PrintTemplatePreviewResponse rendered = renderService.renderPublished(template, buildPrintData(request));
        PrintJobEntity job = printJobService.createDocumentJob(
                String.valueOf(request.order().getId()), node, printer, template, rendered
        );
        return new KitchenPrintResult(job.getId());
    }

    private PrintNodeEntity requirePrintNode() {
        PrintNodeEntity node = printNodeService.getNodeByName("order.confirmed");
        if (node == null || !Boolean.TRUE.equals(node.getEnabled())) {
            throw new IllegalStateException("前台确认订单打印节点未启用");
        }
        if (!"PRINT".equalsIgnoreCase(node.getOperationMode())) {
            throw new IllegalStateException("前台确认订单节点当前不是打印作业方式");
        }
        if (node.getPrinterId() == null || node.getPrinterId().isBlank()) {
            throw new IllegalStateException("前台确认订单节点尚未绑定打印机");
        }
        if (node.getTemplateId() == null || node.getTemplateId().isBlank()) {
            throw new IllegalStateException("前台确认订单节点尚未绑定打印模板");
        }
        return node;
    }

    private PrinterEntity requirePrinter(String printerId) {
        PrinterEntity printer = printerService.getPrinter(printerId);
        if (printer == null) {
            throw new IllegalStateException("打印节点绑定的打印机不存在");
        }
        if (!Boolean.TRUE.equals(printer.getEnabled())) {
            throw new IllegalStateException("打印节点绑定的打印机未启用");
        }
        if (printer.getDriverId() == null || printer.getDriverId().isBlank()) {
            throw new IllegalStateException("打印机尚未配置驱动");
        }
        return printer;
    }

    private ObjectNode buildPrintData(KitchenPrintRequest request) {
        OrderEntity source = request.order();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schema", "jnimble.order-print.v1");

        ObjectNode order = root.putObject("order");
        order.put("id", String.valueOf(source.getId()));
        order.put("orderNo", source.getOrderNo());
        if (source.getTableId() != null) order.put("tableId", String.valueOf(source.getTableId()));
        if (source.getSessionId() != null) order.put("sessionId", String.valueOf(source.getSessionId()));
        if (source.getPartySize() != null) order.put("partySize", source.getPartySize());
        order.put("source", source.getSource());
        order.put("status", source.getStatus());
        order.put("operator", source.getOperator());
        if (source.getCreatedAt() != null) order.put("createdAt", source.getCreatedAt().toString());

        ObjectNode kitchen = root.putObject("kitchen");
        kitchen.put("number", request.ticketNumber());

        ArrayNode items = root.putArray("items");
        for (KitchenQueueItemEntity sourceItem : request.items()) {
            ObjectNode item = items.addObject();
            item.put("number", request.ticketNumber());
            item.put("itemName", sourceItem.getItemName());
            item.put("specification", sourceItem.getSpecification());
            item.put("remark", sourceItem.getRemark());
            item.put("quantity", sourceItem.getQuantity());
        }

        ObjectNode amounts = root.putObject("amounts");
        amounts.put("total", valueOrZero(source.getTotalAmount()));
        amounts.put("discount", valueOrZero(source.getDiscountAmount()));
        amounts.put("final", valueOrZero(source.getFinalAmount()));
        amounts.put("currency", "CNY");
        root.putObject("payment");
        root.putObject("shop");
        root.putObject("extensions");
        return root;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

