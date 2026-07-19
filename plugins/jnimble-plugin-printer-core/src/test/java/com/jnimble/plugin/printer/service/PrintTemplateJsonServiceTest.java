package com.jnimble.plugin.printer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.printer.model.dto.PrintTemplateViolation;
import com.jnimble.plugin.printer.template.PrintTemplateExtensionRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrintTemplateJsonServiceTest {

    private ObjectMapper objectMapper;
    private PrintTemplateExtensionRegistry extensionRegistry;
    private PrintTemplateJsonService jsonService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        extensionRegistry = new PrintTemplateExtensionRegistry(objectMapper);
        jsonService = new PrintTemplateJsonService(objectMapper, extensionRegistry);
    }

    @Test
    void prepareDefinitionShouldCreateFormattedJsonForPaperWidth() {
        JsonNode definition = jsonService.prepareDefinition(null, 58);

        String formatted = jsonService.format(definition);

        assertEquals(58, definition.path("paper").path("widthMm").asInt());
        assertEquals(384, definition.path("paper").path("printableWidthDots").asInt());
        assertTrue(formatted.contains(System.lineSeparator()));
        assertTrue(formatted.contains("  \"paper\""));
        assertEquals(definition, jsonService.parse(formatted));
    }

    @Test
    void formatShouldUseStableObjectKeyOrder() {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("zeta", 1);
        value.put("alpha", 2);

        String formatted = jsonService.format(value);

        assertTrue(formatted.indexOf("\"alpha\"") < formatted.indexOf("\"zeta\""));
    }

    @Test
    void prepareDefinitionShouldNormalizeAmountRowsToBusinessOrder() {
        ObjectNode definition = (ObjectNode) jsonService.defaultDefinition(58);
        ObjectNode config = objectMapper.createObjectNode();
        config.putArray("rows")
                .add("final")
                .add("discount")
                .add("total")
                .add("discount");
        ((ArrayNode) definition.path("rows").get(0).path("blocks"))
                .add(block("total", "core.total-amount", 10000, config));

        JsonNode prepared = jsonService.prepareDefinition(definition, 58);

        JsonNode rows = prepared.path("rows").get(0).path("blocks").get(0)
                .path("config").path("rows");
        assertEquals(3, rows.size());
        assertEquals("total", rows.get(0).asText());
        assertEquals("discount", rows.get(1).asText());
        assertEquals("final", rows.get(2).asText());
    }

    @Test
    void validateShouldRejectRowsWiderThanOneHundredPercent() {
        ObjectNode definition = (ObjectNode) jsonService.defaultDefinition(58);
        ArrayNode blocks = (ArrayNode) definition.path("rows").get(0).path("blocks");
        blocks.add(block("block-1", "core.title", 6000, objectMapper.createObjectNode()));
        blocks.add(block("block-2", "core.total-amount", 6000, objectMapper.createObjectNode()));

        List<PrintTemplateViolation> violations = jsonService.validate(definition, 58, false);

        assertTrue(violations.stream().anyMatch(item -> item.message().contains("不能超过 100%")));
    }

    @Test
    void validateShouldRejectInvalidDetailColumnWidths() {
        ObjectNode definition = (ObjectNode) jsonService.defaultDefinition(80);
        ObjectNode config = objectMapper.createObjectNode();
        ArrayNode columns = config.putArray("columns");
        columns.addObject().put("field", "itemName").put("widthBasisPoints", 7000);
        columns.addObject().put("field", "quantity").put("widthBasisPoints", 2000);
        ((ArrayNode) definition.path("rows").get(0).path("blocks"))
                .add(block("details", "core.order-details", 10000, config));

        List<PrintTemplateViolation> violations = jsonService.validate(definition, 80, true);

        assertTrue(violations.stream().anyMatch(item -> item.message().contains("列宽之和")));
    }

    @Test
    void publishValidationShouldRequireAtLeastOneBlock() {
        JsonNode definition = jsonService.defaultDefinition(58);

        List<PrintTemplateViolation> violations = jsonService.validate(definition, 58, true);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(item -> item.message().contains("至少需要添加一个块")));
    }

    private ObjectNode block(String id, String type, int width, JsonNode config) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("id", id);
        block.put("type", type);
        block.put("provider", "printer-core");
        block.put("providerVersion", 1);
        block.put("widthBasisPoints", width);
        block.put("optional", false);
        block.set("config", config);
        return block;
    }
}
