package com.jnimble.plugin.printer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.printer.model.dto.PrintTemplatePreviewRequest;
import com.jnimble.plugin.printer.model.dto.PrintTemplatePreviewResponse;
import com.jnimble.plugin.printer.model.dto.PrintTemplateViolation;
import com.jnimble.plugin.printer.model.dto.PublishedPrintTemplate;
import com.jnimble.plugin.printer.template.PrintBlockProvider;
import com.jnimble.plugin.printer.template.PrintRenderContext;
import com.jnimble.plugin.printer.template.PrintTemplateExtensionRegistry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PrintTemplateRenderService {

    private final ObjectMapper objectMapper;
    private final PrintTemplateJsonService jsonService;
    private final PrintTemplateExtensionRegistry extensionRegistry;

    public PrintTemplateRenderService(
            ObjectMapper objectMapper,
            PrintTemplateJsonService jsonService,
            PrintTemplateExtensionRegistry extensionRegistry
    ) {
        this.objectMapper = objectMapper;
        this.jsonService = jsonService;
        this.extensionRegistry = extensionRegistry;
    }

    public PrintTemplatePreviewResponse preview(PrintTemplatePreviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Preview request is required");
        }
        int paperWidth = request.paperWidthMm() == null ? 58 : request.paperWidthMm();
        JsonNode definition = jsonService.prepareDefinition(request.definition(), paperWidth);
        List<PrintTemplateViolation> violations = new ArrayList<>(
                jsonService.validate(definition, paperWidth, false)
        );
        JsonNode data = request.data() == null || request.data().isNull()
                ? sampleData()
                : request.data();
        ObjectNode document = renderDocument(definition, data, violations);
        return new PrintTemplatePreviewResponse(
                document,
                jsonService.format(document),
                List.copyOf(violations)
        );
    }

    public PrintTemplatePreviewResponse renderPublished(PublishedPrintTemplate template, JsonNode data) {
        if (template == null) {
            throw new IllegalArgumentException("Published print template is required");
        }
        List<PrintTemplateViolation> violations = new ArrayList<>(
                jsonService.validate(template.definition(), template.paperWidthMm(), true)
        );
        ObjectNode document = renderDocument(template.definition(), data, violations);
        if (!violations.isEmpty()) {
            throw new PrintTemplateValidationException(violations);
        }
        return new PrintTemplatePreviewResponse(document, jsonService.format(document), List.of());
    }

    public ObjectNode renderDocument(
            JsonNode definition,
            JsonNode data,
            List<PrintTemplateViolation> violations
    ) {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schema", "jnimble.print-document.v1");
        document.put("schemaVersion", 1);
        document.set("paper", definition.path("paper").deepCopy());
        ArrayNode outputRows = document.putArray("rows");
        PrintRenderContext context = new PrintRenderContext(data, objectMapper);

        JsonNode rows = definition.path("rows");
        if (!rows.isArray()) {
            return document;
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            JsonNode row = rows.get(rowIndex);
            ObjectNode outputRow = outputRows.addObject();
            outputRow.put("id", row.path("id").asText("row-" + (rowIndex + 1)));
            ArrayNode cells = outputRow.putArray("cells");
            JsonNode blocks = row.path("blocks");
            if (!blocks.isArray()) {
                continue;
            }
            for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
                JsonNode block = blocks.get(blockIndex);
                String blockPath = "rows[" + rowIndex + "].blocks[" + blockIndex + "]";
                String type = block.path("type").asText();
                PrintBlockProvider provider = extensionRegistry.findProvider(type).orElse(null);
                if (provider == null) {
                    if (!block.path("optional").asBoolean(false)) {
                        violations.add(new PrintTemplateViolation(blockPath + ".type", "块提供者不可用：" + type));
                    }
                    continue;
                }

                try {
                    ObjectNode cell = cells.addObject();
                    cell.put("blockId", block.path("id").asText());
                    cell.put("blockType", type);
                    cell.put("provider", provider.descriptor().provider());
                    cell.put("providerVersion", provider.descriptor().version());
                    cell.put("widthBasisPoints", block.path("widthBasisPoints").asInt(10000));
                    ArrayNode elements = cell.putArray("elements");
                    JsonNode element = provider.render(block.path("config"), context);
                    if (element != null && !element.isNull()) {
                        elements.add(element);
                    }
                } catch (RuntimeException exception) {
                    violations.add(new PrintTemplateViolation(blockPath, "块渲染失败：" + exception.getMessage()));
                }
            }
        }
        document.put("dataDigest", jsonService.digest(data));
        document.put("documentDigest", jsonService.digest(document));
        return document;
    }

    public JsonNode sampleData() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schema", "jnimble.order-print.v1");

        ObjectNode order = root.putObject("order");
        order.put("id", "10001");
        order.put("orderNo", "D202607140001");
        order.put("tableId", "8");
        order.put("source", "TABLE");
        order.put("partySize", 4);
        order.put("status", "SETTLED");
        order.put("operator", "admin");
        order.put("createdAt", OffsetDateTime.now().minusMinutes(50).toString());
        order.put("settledAt", OffsetDateTime.now().toString());

        ArrayNode items = root.putArray("items");
        addItem(items, "宫保鸡丁", "大份 / 微辣", 1, "28.00", "28.00", "少盐");
        addItem(items, "米饭", "标准", 2, "3.00", "6.00", "");

        ObjectNode amounts = root.putObject("amounts");
        amounts.put("total", new BigDecimal("34.00"));
        amounts.put("discount", new BigDecimal("0.00"));
        amounts.put("final", new BigDecimal("34.00"));
        amounts.put("currency", "CNY");

        ObjectNode payment = root.putObject("payment");
        payment.put("method", "WECHAT");
        payment.put("settlementQr", "https://pay.example.com/orders/D202607140001");
        root.putObject("shop").put("name", "JNimble 示例餐厅");
        root.putObject("extensions");
        return root;
    }

    private void addItem(
            ArrayNode items,
            String name,
            String specification,
            int quantity,
            String unitPrice,
            String subtotal,
            String remark
    ) {
        ObjectNode item = items.addObject();
        item.put("number", "大堂1号");
        item.put("itemName", name);
        item.put("specification", specification);
        item.put("quantity", quantity);
        item.put("unitPrice", new BigDecimal(unitPrice));
        item.put("subtotal", new BigDecimal(subtotal));
        item.put("remark", remark);
    }
}
