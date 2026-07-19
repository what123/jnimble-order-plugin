package com.jnimble.plugin.printer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.printer.model.dto.PrintTemplateViolation;
import com.jnimble.plugin.printer.template.PrintBlockDescriptor;
import com.jnimble.plugin.printer.template.PrintBlockProvider;
import com.jnimble.plugin.printer.template.PrintTemplateExtensionRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PrintTemplateJsonService {

    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_ROWS = 100;
    private static final int MAX_BLOCKS = 200;
    private static final int MIN_BLOCK_WIDTH = 500;
    private static final int FULL_WIDTH = 10000;
    private static final List<String> AMOUNT_ROW_ORDER = List.of("total", "discount", "final");

    private final ObjectMapper objectMapper;
    private final PrintTemplateExtensionRegistry extensionRegistry;

    public PrintTemplateJsonService(
            ObjectMapper objectMapper,
            PrintTemplateExtensionRegistry extensionRegistry
    ) {
        this.objectMapper = objectMapper.copy()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.extensionRegistry = extensionRegistry;
    }

    public JsonNode prepareDefinition(JsonNode definition, int paperWidthMm) {
        JsonNode prepared = definition == null || definition.isNull()
                ? defaultDefinition(paperWidthMm)
                : definition.deepCopy();
        if (!prepared.isObject()) {
            throw new PrintTemplateValidationException(List.of(
                    new PrintTemplateViolation("$", "模板定义必须是 JSON 对象")
            ));
        }
        ObjectNode root = (ObjectNode) prepared;
        if (!root.has("schemaVersion")) {
            root.put("schemaVersion", SCHEMA_VERSION);
        }
        ObjectNode paper = root.withObject("/paper");
        if (!paper.has("widthMm")) {
            paper.put("widthMm", paperWidthMm);
        }
        if (!paper.has("dpi")) {
            paper.put("dpi", 203);
        }
        if (!paper.has("printableWidthDots")) {
            paper.put("printableWidthDots", paperWidthMm == 80 ? 576 : 384);
        }
        if (!root.has("rows")) {
            root.putArray("rows");
        }
        normalizeAmountRows(root);
        return normalize(root);
    }

    private void normalizeAmountRows(ObjectNode root) {
        JsonNode rows = root.path("rows");
        if (!rows.isArray()) {
            return;
        }
        for (JsonNode row : rows) {
            JsonNode blocks = row.path("blocks");
            if (!blocks.isArray()) {
                continue;
            }
            for (JsonNode block : blocks) {
                if (!"core.total-amount".equals(block.path("type").asText())) {
                    continue;
                }
                JsonNode config = block.path("config");
                JsonNode configuredRows = config.path("rows");
                if (!config.isObject() || !configuredRows.isArray()) {
                    continue;
                }
                List<String> keys = new ArrayList<>();
                configuredRows.forEach(item -> {
                    String key = item.asText();
                    if (!keys.contains(key)) {
                        keys.add(key);
                    }
                });
                ArrayNode normalizedRows = objectMapper.createArrayNode();
                AMOUNT_ROW_ORDER.forEach(key -> {
                    if (keys.contains(key)) {
                        normalizedRows.add(key);
                    }
                });
                keys.stream()
                        .filter(key -> !AMOUNT_ROW_ORDER.contains(key))
                        .forEach(normalizedRows::add);
                ((ObjectNode) config).set("rows", normalizedRows);
            }
        }
    }

    public JsonNode defaultDefinition(int paperWidthMm) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        ObjectNode paper = root.putObject("paper");
        paper.put("widthMm", paperWidthMm);
        paper.put("dpi", 203);
        paper.put("printableWidthDots", paperWidthMm == 80 ? 576 : 384);
        ObjectNode row = root.putArray("rows").addObject();
        row.put("id", "row-1");
        row.putArray("blocks");
        return root;
    }

    public List<PrintTemplateViolation> validate(
            JsonNode definition,
            int paperWidthMm,
            boolean forPublish
    ) {
        List<PrintTemplateViolation> violations = new ArrayList<>();
        if (definition == null || !definition.isObject()) {
            return List.of(new PrintTemplateViolation("$", "模板定义必须是 JSON 对象"));
        }
        if (definition.path("schemaVersion").asInt(-1) != SCHEMA_VERSION) {
            violations.add(new PrintTemplateViolation(
                    "schemaVersion",
                    "当前仅支持 schemaVersion " + SCHEMA_VERSION
            ));
        }
        int definitionPaperWidth = definition.path("paper").path("widthMm").asInt(0);
        if (paperWidthMm != 58 && paperWidthMm != 80) {
            violations.add(new PrintTemplateViolation("paper.widthMm", "纸宽只能是 58mm 或 80mm"));
        } else if (definitionPaperWidth != paperWidthMm) {
            violations.add(new PrintTemplateViolation("paper.widthMm", "模板纸宽与基本信息不一致"));
        }

        JsonNode rows = definition.get("rows");
        if (rows == null || !rows.isArray()) {
            violations.add(new PrintTemplateViolation("rows", "rows 必须是数组"));
            return violations;
        }
        if (rows.size() > MAX_ROWS) {
            violations.add(new PrintTemplateViolation("rows", "模板行数不能超过 " + MAX_ROWS));
        }

        Set<String> rowIds = new HashSet<>();
        Set<String> blockIds = new HashSet<>();
        int blockCount = 0;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            JsonNode row = rows.get(rowIndex);
            String rowPath = "rows[" + rowIndex + "]";
            if (!row.isObject()) {
                violations.add(new PrintTemplateViolation(rowPath, "行必须是 JSON 对象"));
                continue;
            }
            String rowId = row.path("id").asText();
            if (rowId.isBlank()) {
                violations.add(new PrintTemplateViolation(rowPath + ".id", "行 ID 不能为空"));
            } else if (!rowIds.add(rowId)) {
                violations.add(new PrintTemplateViolation(rowPath + ".id", "行 ID 不能重复"));
            }

            JsonNode blocks = row.get("blocks");
            if (blocks == null || !blocks.isArray()) {
                violations.add(new PrintTemplateViolation(rowPath + ".blocks", "blocks 必须是数组"));
                continue;
            }
            int rowWidth = 0;
            for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
                blockCount++;
                JsonNode block = blocks.get(blockIndex);
                String blockPath = rowPath + ".blocks[" + blockIndex + "]";
                if (!block.isObject()) {
                    violations.add(new PrintTemplateViolation(blockPath, "块必须是 JSON 对象"));
                    continue;
                }
                String blockId = block.path("id").asText();
                if (blockId.isBlank()) {
                    violations.add(new PrintTemplateViolation(blockPath + ".id", "块 ID 不能为空"));
                } else if (!blockIds.add(blockId)) {
                    violations.add(new PrintTemplateViolation(blockPath + ".id", "块 ID 不能重复"));
                }
                String type = block.path("type").asText();
                int width = block.path("widthBasisPoints").asInt(0);
                rowWidth += width;
                if (width < MIN_BLOCK_WIDTH || width > FULL_WIDTH) {
                    violations.add(new PrintTemplateViolation(
                            blockPath + ".widthBasisPoints",
                            "块宽必须在 5% 到 100% 之间"
                    ));
                }

                PrintBlockProvider provider = extensionRegistry.findProvider(type).orElse(null);
                boolean optional = block.path("optional").asBoolean(false);
                if (provider == null) {
                    if (forPublish && !optional) {
                        violations.add(new PrintTemplateViolation(
                                blockPath + ".type",
                                "块提供者不可用：" + type
                        ));
                    }
                    continue;
                }
                PrintBlockDescriptor descriptor = provider.descriptor();
                if (width > 0 && width < descriptor.minWidthBasisPoints()) {
                    violations.add(new PrintTemplateViolation(
                            blockPath + ".widthBasisPoints",
                            descriptor.label() + "最小宽度为 "
                                    + descriptor.minWidthBasisPoints() / 100 + "%"
                    ));
                }
                int configuredVersion = block.path("providerVersion").asInt(descriptor.version());
                if (configuredVersion > descriptor.version()) {
                    violations.add(new PrintTemplateViolation(
                            blockPath + ".providerVersion",
                            "块版本高于当前提供者版本"
                    ));
                }
                for (PrintTemplateViolation violation : provider.validate(block.path("config"))) {
                    violations.add(new PrintTemplateViolation(
                            blockPath + "." + violation.path(),
                            violation.message()
                    ));
                }
            }
            if (rowWidth > FULL_WIDTH) {
                violations.add(new PrintTemplateViolation(rowPath + ".blocks", "同一行块宽之和不能超过 100%"));
            }
        }
        if (blockCount > MAX_BLOCKS) {
            violations.add(new PrintTemplateViolation("rows", "模板块数量不能超过 " + MAX_BLOCKS));
        }
        if (forPublish && blockCount == 0) {
            violations.add(new PrintTemplateViolation("rows", "发布模板前至少需要添加一个块"));
        }
        return violations;
    }

    public void validateOrThrow(JsonNode definition, int paperWidthMm, boolean forPublish) {
        List<PrintTemplateViolation> violations = validate(definition, paperWidthMm, forPublish);
        if (!violations.isEmpty()) {
            throw new PrintTemplateValidationException(violations);
        }
    }

    public String format(JsonNode definition) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalize(definition));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to format print template JSON", exception);
        }
    }

    public JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stored print template JSON is invalid", exception);
        }
    }

    public String providerSnapshot(JsonNode definition) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        JsonNode rows = definition.path("rows");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                for (JsonNode block : row.path("blocks")) {
                    String type = block.path("type").asText();
                    extensionRegistry.findProvider(type).ifPresent(provider -> {
                        ObjectNode entry = snapshot.putObject(type);
                        entry.put("provider", provider.descriptor().provider());
                        entry.put("version", provider.descriptor().version());
                    });
                }
            }
        }
        return format(snapshot);
    }

    public String digest(JsonNode value) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(normalize(value)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to digest print JSON", exception);
        }
    }

    private JsonNode normalize(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return value;
        }
        if (value.isArray()) {
            ArrayNode normalized = objectMapper.createArrayNode();
            value.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        fields.addAll(value.properties());
        fields.sort(Comparator.comparing(Map.Entry::getKey));
        fields.forEach(entry -> normalized.set(entry.getKey(), normalize(entry.getValue())));
        return normalized;
    }
}
