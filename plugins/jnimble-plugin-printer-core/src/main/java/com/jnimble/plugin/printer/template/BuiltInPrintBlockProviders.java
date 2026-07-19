package com.jnimble.plugin.printer.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.printer.model.dto.PrintTemplateViolation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BuiltInPrintBlockProviders {

    private BuiltInPrintBlockProviders() {
    }

    static List<PrintBlockProvider> create(ObjectMapper objectMapper) {
        return List.of(
                new TitleProvider(objectMapper),
                new DetailsProvider(objectMapper),
                new KitchenItemsProvider(objectMapper),
                new TotalProvider(objectMapper),
                new QrProvider(objectMapper)
        );
    }

    private abstract static class BuiltInProvider implements PrintBlockProvider {

        protected final ObjectMapper objectMapper;

        private BuiltInProvider(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        protected ObjectNode element(String kind) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("kind", kind);
            return result;
        }

        protected String text(JsonNode config, String field, String fallback) {
            JsonNode value = config == null ? null : config.get(field);
            return value == null || value.isNull() ? fallback : value.asText(fallback);
        }

        protected boolean bool(JsonNode config, String field, boolean fallback) {
            JsonNode value = config == null ? null : config.get(field);
            return value == null || value.isNull() ? fallback : value.asBoolean(fallback);
        }

        protected void applyTypography(
                ObjectNode result,
                JsonNode config,
                String defaultAlign,
                String defaultSize,
                boolean defaultBold
        ) {
            result.put("align", text(config, "align", defaultAlign));
            result.put("size", text(config, "size", defaultSize));
            result.put("bold", bool(config, "bold", defaultBold));
        }

        protected List<PrintTemplateViolation> validateTableColumns(JsonNode config, String blockLabel) {
            List<PrintTemplateViolation> violations = new ArrayList<>();
            JsonNode columns = config == null ? null : config.get("columns");
            if (columns == null || !columns.isArray() || columns.isEmpty()) {
                violations.add(new PrintTemplateViolation("config.columns", blockLabel + "至少需要一列"));
                return violations;
            }
            int totalWidth = 0;
            for (int index = 0; index < columns.size(); index++) {
                JsonNode column = columns.get(index);
                if (column.path("field").asText().isBlank()) {
                    violations.add(new PrintTemplateViolation(
                            "config.columns[" + index + "].field",
                            blockLabel + "列字段不能为空"
                    ));
                }
                totalWidth += column.path("widthBasisPoints").asInt(0);
            }
            if (totalWidth != 10000) {
                violations.add(new PrintTemplateViolation("config.columns", blockLabel + "列宽之和必须等于 100%"));
            }
            return violations;
        }

        protected JsonNode renderTable(JsonNode config, PrintRenderContext context, boolean defaultBold) {
            ObjectNode result = element("TABLE");
            applyTypography(result, config, "AUTO", "NORMAL", defaultBold);
            result.put("showHeader", bool(config, "showHeader", true));

            ArrayNode outputColumns = result.putArray("columns");
            JsonNode columns = config == null ? null : config.get("columns");
            if (columns != null && columns.isArray()) {
                columns.forEach(outputColumns::add);
            }

            ArrayNode outputRows = result.putArray("rows");
            JsonNode items = context.resolve("items");
            if (items.isArray() && columns != null && columns.isArray()) {
                for (JsonNode item : items) {
                    ObjectNode outputRow = outputRows.addObject();
                    ArrayNode cells = outputRow.putArray("cells");
                    for (JsonNode column : columns) {
                        String field = column.path("field").asText();
                        ObjectNode cell = cells.addObject();
                        cell.put("field", field);
                        cell.put("text", context.formatValue(item.path(field), column.path("format").asText()));
                        cell.put("align", column.path("align").asText("LEFT"));
                    }
                }
            }
            return result;
        }

        protected Map<String, Object> column(
                String field,
                String title,
                int width,
                String align,
                String format
        ) {
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("field", field);
            column.put("title", title);
            column.put("widthBasisPoints", width);
            column.put("align", align);
            column.put("overflow", "WRAP");
            if (format != null) {
                column.put("format", format);
            }
            return column;
        }
    }

    private static final class TitleProvider extends BuiltInProvider {

        private TitleProvider(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public PrintBlockDescriptor descriptor() {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("text", "${shop.name}");
            defaults.put("align", "CENTER");
            defaults.put("size", "LARGE");
            defaults.put("bold", true);
            defaults.put("overflow", "WRAP");
            return new PrintBlockDescriptor(
                    "core.title",
                    "printer-core",
                    1,
                    "标题",
                    "门店名、订单号或固定标题",
                    10000,
                    2000,
                    defaults
            );
        }

        @Override
        public JsonNode render(JsonNode config, PrintRenderContext context) {
            ObjectNode result = element("TEXT");
            result.put("text", context.interpolate(text(config, "text", "")));
            applyTypography(result, config, "CENTER", "LARGE", true);
            result.put("overflow", text(config, "overflow", "WRAP"));
            return result;
        }
    }

    private static final class DetailsProvider extends BuiltInProvider {

        private DetailsProvider(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public PrintBlockDescriptor descriptor() {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("align", "AUTO");
            defaults.put("size", "NORMAL");
            defaults.put("bold", false);
            defaults.put("showHeader", true);
            defaults.put("columns", List.of(
                    column("itemName", "商品", 5000, "LEFT", null),
                    column("quantity", "数量", 1500, "RIGHT", null),
                    column("unitPrice", "单价", 1750, "RIGHT", "MONEY"),
                    column("subtotal", "小计", 1750, "RIGHT", "MONEY")
            ));
            return new PrintBlockDescriptor(
                    "core.order-details",
                    "printer-core",
                    1,
                    "订单明细",
                    "可配置商品、数量、单价、小计和备注列",
                    10000,
                    3000,
                    defaults
            );
        }

        @Override
        public List<PrintTemplateViolation> validate(JsonNode config) {
            return validateTableColumns(config, "明细块");
        }

        @Override
        public JsonNode render(JsonNode config, PrintRenderContext context) {
            return renderTable(config, context, false);
        }
    }

    private static final class KitchenItemsProvider extends BuiltInProvider {

        private KitchenItemsProvider(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public PrintBlockDescriptor descriptor() {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("align", "AUTO");
            defaults.put("size", "NORMAL");
            defaults.put("bold", true);
            defaults.put("showHeader", true);
            defaults.put("columns", List.of(
                    column("number", "编号", 2000, "LEFT", null),
                    column("itemName", "菜品", 3000, "LEFT", null),
                    column("specification", "规格", 2000, "LEFT", null),
                    column("remark", "备注", 2000, "LEFT", null),
                    column("quantity", "数量", 1000, "RIGHT", null)
            ));
            return new PrintBlockDescriptor(
                    "core.kitchen-items",
                    "printer-core",
                    1,
                    "后厨菜品",
                    "供后厨查看编号、菜品、规格、备注和数量",
                    10000,
                    3000,
                    defaults
            );
        }

        @Override
        public List<PrintTemplateViolation> validate(JsonNode config) {
            return validateTableColumns(config, "后厨菜品块");
        }

        @Override
        public JsonNode render(JsonNode config, PrintRenderContext context) {
            return renderTable(config, context, true);
        }
    }

    private static final class TotalProvider extends BuiltInProvider {

        private static final List<String> ROW_ORDER = List.of("total", "discount", "final");
        private static final Map<String, String> LABELS = Map.of(
                "total", "订单原价",
                "discount", "优惠金额",
                "final", "应该收金额"
        );

        private TotalProvider(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public PrintBlockDescriptor descriptor() {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("rows", List.of("final"));
            defaults.put("align", "JUSTIFY");
            defaults.put("size", "NORMAL");
            defaults.put("bold", true);
            defaults.put("emphasis", "STRONG");
            return new PrintBlockDescriptor(
                    "core.total-amount",
                    "printer-core",
                    1,
                    "总金额",
                    "订单原价、优惠金额和应该收金额",
                    10000,
                    2500,
                    defaults
            );
        }

        @Override
        public JsonNode render(JsonNode config, PrintRenderContext context) {
            ObjectNode result = element("AMOUNT_ROWS");
            String emphasis = text(config, "emphasis", "STRONG");
            result.put("emphasis", emphasis);
            applyTypography(
                    result,
                    config,
                    "JUSTIFY",
                    "NORMAL",
                    "STRONG".equals(emphasis)
            );
            JsonNode configuredRows = config == null ? null : config.get("rows");
            if (configuredRows == null || !configuredRows.isArray() || configuredRows.isEmpty()) {
                configuredRows = objectMapper.valueToTree(List.of("final"));
            }
            List<String> configuredKeys = new ArrayList<>();
            for (JsonNode configuredRow : configuredRows) {
                String key = configuredRow.asText();
                if (!configuredKeys.contains(key)) {
                    configuredKeys.add(key);
                }
            }
            ArrayNode rows = result.putArray("rows");
            for (String key : ROW_ORDER) {
                if (configuredKeys.contains(key)) {
                    addAmountRow(rows, key, context);
                }
            }
            for (String key : configuredKeys) {
                if (!ROW_ORDER.contains(key)) {
                    addAmountRow(rows, key, context);
                }
            }
            return result;
        }

        private void addAmountRow(ArrayNode rows, String key, PrintRenderContext context) {
            ObjectNode row = rows.addObject();
            row.put("key", key);
            row.put("label", LABELS.getOrDefault(key, key));
            row.put("value", context.formatValue(context.resolve("amounts." + key), "MONEY"));
        }
    }

    private static final class QrProvider extends BuiltInProvider {

        private QrProvider(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public PrintBlockDescriptor descriptor() {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("source", "payment.settlementQr");
            defaults.put("align", "CENTER");
            defaults.put("size", "NORMAL");
            defaults.put("bold", false);
            defaults.put("errorCorrection", "M");
            defaults.put("missingData", "HIDE");
            defaults.put("caption", "扫码结账");
            return new PrintBlockDescriptor(
                    "core.settlement-qr",
                    "printer-core",
                    1,
                    "结账二维码",
                    "从指定数据字段生成结账二维码",
                    10000,
                    4000,
                    defaults
            );
        }

        @Override
        public JsonNode render(JsonNode config, PrintRenderContext context) {
            String source = text(config, "source", "payment.settlementQr");
            String payload = context.text(source);
            String missingData = text(config, "missingData", "HIDE");
            if (payload.isBlank() && "ERROR".equals(missingData)) {
                throw new IllegalArgumentException("二维码数据为空：" + source);
            }
            ObjectNode result = element("QR");
            result.put("source", source);
            result.put("payload", payload);
            result.put("hidden", payload.isBlank() && "HIDE".equals(missingData));
            applyTypography(result, config, "CENTER", "NORMAL", false);
            result.put("errorCorrection", text(config, "errorCorrection", "M"));
            result.put("caption", text(config, "caption", "扫码结账"));
            return result;
        }
    }
}
