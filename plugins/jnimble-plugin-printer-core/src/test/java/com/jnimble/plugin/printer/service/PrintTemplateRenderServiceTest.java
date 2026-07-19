package com.jnimble.plugin.printer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.printer.model.dto.PrintTemplatePreviewRequest;
import com.jnimble.plugin.printer.model.dto.PrintTemplatePreviewResponse;
import com.jnimble.plugin.printer.template.PrintBlockDescriptor;
import com.jnimble.plugin.printer.template.PrintTemplateExtensionRegistry;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrintTemplateRenderServiceTest {

    private ObjectMapper objectMapper;
    private PrintTemplateExtensionRegistry extensionRegistry;
    private PrintTemplateJsonService jsonService;
    private PrintTemplateRenderService renderService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        extensionRegistry = new PrintTemplateExtensionRegistry(objectMapper);
        jsonService = new PrintTemplateJsonService(objectMapper, extensionRegistry);
        renderService = new PrintTemplateRenderService(objectMapper, jsonService, extensionRegistry);
    }

    @Test
    void previewShouldRenderAllBuiltInBlocksAsPrintDocumentJson() {
        ObjectNode definition = (ObjectNode) jsonService.defaultDefinition(58);
        ArrayNode rows = (ArrayNode) definition.path("rows");
        rows.removeAll();
        Map<String, PrintBlockDescriptor> descriptors = extensionRegistry.descriptors().stream()
                .collect(Collectors.toMap(PrintBlockDescriptor::type, Function.identity()));

        addRow(rows, "title", descriptors.get("core.title"));
        addRow(rows, "details", descriptors.get("core.order-details"));
        addRow(rows, "kitchen", descriptors.get("core.kitchen-items"));
        ObjectNode totalBlock = addRow(rows, "total", descriptors.get("core.total-amount"));
        ArrayNode configuredAmountRows = ((ObjectNode) totalBlock.path("config")).putArray("rows");
        configuredAmountRows.add("final").add("discount").add("total");
        addRow(rows, "qr", descriptors.get("core.settlement-qr"));

        PrintTemplatePreviewResponse response = renderService.preview(
                new PrintTemplatePreviewRequest(58, definition, null)
        );

        assertTrue(response.violations().isEmpty());
        assertEquals("jnimble.print-document.v1", response.document().path("schema").asText());
        assertEquals(5, response.document().path("rows").size());
        assertEquals("TEXT", kind(response.document(), 0));
        assertEquals("TABLE", kind(response.document(), 1));
        assertEquals("TABLE", kind(response.document(), 2));
        assertEquals("AMOUNT_ROWS", kind(response.document(), 3));
        assertEquals("QR", kind(response.document(), 4));
        assertTypography(element(response.document(), 0), "CENTER", "LARGE", true);
        assertTypography(element(response.document(), 1), "AUTO", "NORMAL", false);
        assertTypography(element(response.document(), 2), "AUTO", "NORMAL", true);
        assertTypography(element(response.document(), 3), "JUSTIFY", "NORMAL", true);
        assertTypography(element(response.document(), 4), "CENTER", "NORMAL", false);
        JsonNode kitchen = element(response.document(), 2);
        assertEquals("number", kitchen.path("columns").get(0).path("field").asText());
        assertEquals("itemName", kitchen.path("columns").get(1).path("field").asText());
        assertEquals("specification", kitchen.path("columns").get(2).path("field").asText());
        assertEquals("remark", kitchen.path("columns").get(3).path("field").asText());
        assertEquals("quantity", kitchen.path("columns").get(4).path("field").asText());
        JsonNode kitchenCells = kitchen.path("rows").get(0).path("cells");
        assertEquals("大堂1号", kitchenCells.get(0).path("text").asText());
        assertEquals("宫保鸡丁", kitchenCells.get(1).path("text").asText());
        assertEquals("大份 / 微辣", kitchenCells.get(2).path("text").asText());
        assertEquals("少盐", kitchenCells.get(3).path("text").asText());
        assertEquals("1", kitchenCells.get(4).path("text").asText());
        JsonNode amountRows = element(response.document(), 3).path("rows");
        assertEquals("total", amountRows.get(0).path("key").asText());
        assertEquals("订单原价", amountRows.get(0).path("label").asText());
        assertEquals("discount", amountRows.get(1).path("key").asText());
        assertEquals("优惠金额", amountRows.get(1).path("label").asText());
        assertEquals("final", amountRows.get(2).path("key").asText());
        assertEquals("应该收金额", amountRows.get(2).path("label").asText());
        assertFalse(response.document().path("documentDigest").asText().isBlank());
        assertTrue(response.documentJson().contains(System.lineSeparator()));
    }

    @Test
    void previewShouldApplyTypographyOverridesToEveryBuiltInBlock() {
        ObjectNode definition = (ObjectNode) jsonService.defaultDefinition(58);
        ArrayNode rows = (ArrayNode) definition.path("rows");
        rows.removeAll();
        Map<String, PrintBlockDescriptor> descriptors = extensionRegistry.descriptors().stream()
                .collect(Collectors.toMap(PrintBlockDescriptor::type, Function.identity()));

        for (String type : List.of(
                "core.title",
                "core.order-details",
                "core.kitchen-items",
                "core.total-amount",
                "core.settlement-qr"
        )) {
            ObjectNode block = addRow(rows, type, descriptors.get(type));
            ObjectNode config = (ObjectNode) block.path("config");
            config.put("align", "RIGHT");
            config.put("size", "LARGE");
            config.put("bold", true);
        }

        PrintTemplatePreviewResponse response = renderService.preview(
                new PrintTemplatePreviewRequest(58, definition, null)
        );

        assertTrue(response.violations().isEmpty());
        for (int index = 0; index < rows.size(); index++) {
            assertTypography(element(response.document(), index), "RIGHT", "LARGE", true);
        }
    }

    private ObjectNode addRow(ArrayNode rows, String id, PrintBlockDescriptor descriptor) {
        ObjectNode row = rows.addObject();
        row.put("id", "row-" + id);
        ObjectNode block = row.putArray("blocks").addObject();
        block.put("id", "block-" + id);
        block.put("type", descriptor.type());
        block.put("provider", descriptor.provider());
        block.put("providerVersion", descriptor.version());
        block.put("widthBasisPoints", descriptor.defaultWidthBasisPoints());
        block.put("optional", false);
        block.set("config", objectMapper.valueToTree(descriptor.defaultConfig()));
        return block;
    }

    private String kind(JsonNode document, int rowIndex) {
        return element(document, rowIndex).path("kind").asText();
    }

    private JsonNode element(JsonNode document, int rowIndex) {
        return document.path("rows").get(rowIndex)
                .path("cells").get(0)
                .path("elements").get(0);
    }

    private void assertTypography(JsonNode element, String align, String size, boolean bold) {
        assertEquals(align, element.path("align").asText());
        assertEquals(size, element.path("size").asText());
        assertEquals(bold, element.path("bold").asBoolean());
    }
}
