package com.jnimble.plugin.printer.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeieEscPosRendererTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FeieEscPosRenderer renderer = new FeieEscPosRenderer(objectMapper);

    @Test
    void shouldReturnEmptyForBlankInput() {
        assertEquals("", renderer.render(null));
        assertEquals("", renderer.render(""));
        assertEquals("", renderer.render("   "));
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> renderer.render("not-json"));
    }

    @Test
    void shouldRenderLargeTextWithCenterDoubleHeightTag() {
        ObjectNode document = singleTextElement("测试门店", "CENTER", "LARGE", true);
        String output = renderer.render(document.toString());

        assertTrue(output.contains("<C double-height>测试门店</C>"), "实际:" + output);
    }

    @Test
    void shouldRenderBoldTextWithBoldTag() {
        ObjectNode document = singleTextElement("总金额", "LEFT", "NORMAL", true);
        String output = renderer.render(document.toString());

        assertTrue(output.contains("<BOLD>总金额</BOLD>"), "实际:" + output);
    }

    @Test
    void shouldRenderCenterTextWithoutBoldWithCenterTag() {
        ObjectNode document = singleTextElement("备注", "CENTER", "NORMAL", false);
        String output = renderer.render(document.toString());

        assertTrue(output.contains("<CENTER>备注</CENTER>"), "实际:" + output);
    }

    @Test
    void shouldRenderPlainTextWithoutTagsWhenLeftNormalNonBold() {
        ObjectNode document = singleTextElement("普通", "LEFT", "NORMAL", false);
        String output = renderer.render(document.toString());

        assertFalse(output.contains("<"));
        assertTrue(output.contains("普通"));
    }

    @Test
    void shouldEscapeHtmlEntitiesInText() {
        ObjectNode document = singleTextElement("<script>", "LEFT", "NORMAL", false);
        String output = renderer.render(document.toString());

        assertTrue(output.contains("&lt;script&gt;"));
    }

    @Test
    void shouldRenderTableHeaderAndRows() {
        ObjectNode document = documentWithTable();
        String output = renderer.render(document.toString());

        assertTrue(output.contains("商品"), "应含表头");
        assertTrue(output.contains("宫保鸡丁"), "应含单元格数据");
        assertTrue(output.contains("---"), "应含分隔行,实际:" + output);
    }

    @Test
    void shouldRenderAmountRowsAsJustifiedBold() {
        ObjectNode document = documentWithAmountRows();
        String output = renderer.render(document.toString());

        assertTrue(output.contains("<BOLD>"), "应含 BOLD,实际:" + output);
        assertTrue(output.contains("应该收金额"), "应含金额行标签");
        assertTrue(output.contains("34.00"));
    }

    @Test
    void shouldRenderQrWithQrTag() {
        ObjectNode document = documentWithQr();
        String output = renderer.render(document.toString());

        assertTrue(output.contains("<QR>https://pay.example.com</QR>"));
        assertTrue(output.contains("<CENTER>扫码结账</CENTER>"));
    }

    @Test
    void shouldSkipHiddenQr() {
        ObjectNode document = documentWithQr();
        ObjectNode element = (ObjectNode) document.path("rows").get(0).path("cells").get(0).path("elements").get(0);
        element.put("hidden", true);
        String output = renderer.render(document.toString());

        assertFalse(output.contains("<QR>"));
    }

    @Test
    void shouldSkipUnknownBlockKind() {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schema", "jnimble.print-document.v1");
        ObjectNode row = document.putArray("rows").addObject();
        ObjectNode cell = row.putArray("cells").addObject();
        ObjectNode element = cell.putArray("elements").addObject();
        element.put("kind", "UNKNOWN_FUTURE_BLOCK");

        String output = renderer.render(document.toString());
        assertNotNull(output);
        assertFalse(output.contains("UNKNOWN_FUTURE_BLOCK"));
    }

    private ObjectNode singleTextElement(String text, String align, String size, boolean bold) {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schema", "jnimble.print-document.v1");
        ObjectNode row = document.putArray("rows").addObject();
        ObjectNode cell = row.putArray("cells").addObject();
        ObjectNode element = cell.putArray("elements").addObject();
        element.put("kind", "TEXT");
        element.put("text", text);
        element.put("align", align);
        element.put("size", size);
        element.put("bold", bold);
        return document;
    }

    private ObjectNode documentWithTable() {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schema", "jnimble.print-document.v1");
        ObjectNode paper = document.putObject("paper");
        paper.put("widthMm", 58);
        ObjectNode row = document.putArray("rows").addObject();
        ObjectNode cell = row.putArray("cells").addObject();
        ObjectNode element = cell.putArray("elements").addObject();
        element.put("kind", "TABLE");
        element.put("showHeader", true);
        element.put("bold", false);
        ArrayNode columns = element.putArray("columns");
        columns.add(tableColumn("itemName", "商品", 5000, "LEFT"));
        columns.add(tableColumn("quantity", "数量", 1500, "RIGHT"));
        ArrayNode rows = element.putArray("rows");
        ObjectNode dataRow = rows.addObject();
        dataRow.putObject("itemName").put("text", "宫保鸡丁");
        dataRow.putObject("quantity").put("text", "1");
        return document;
    }

    private ObjectNode tableColumn(String field, String title, int width, String align) {
        ObjectNode column = objectMapper.createObjectNode();
        column.put("field", field);
        column.put("title", title);
        column.put("widthBasisPoints", width);
        column.put("align", align);
        return column;
    }

    private ObjectNode documentWithAmountRows() {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schema", "jnimble.print-document.v1");
        ObjectNode paper = document.putObject("paper");
        paper.put("widthMm", 58);
        ObjectNode row = document.putArray("rows").addObject();
        ObjectNode cell = row.putArray("cells").addObject();
        ObjectNode element = cell.putArray("elements").addObject();
        element.put("kind", "AMOUNT_ROWS");
        element.put("bold", true);
        ArrayNode rows = element.putArray("rows");
        ObjectNode amountRow = rows.addObject();
        amountRow.put("key", "final");
        amountRow.put("label", "应该收金额");
        amountRow.put("value", "34.00");
        return document;
    }

    private ObjectNode documentWithQr() {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schema", "jnimble.print-document.v1");
        ObjectNode row = document.putArray("rows").addObject();
        ObjectNode cell = row.putArray("cells").addObject();
        ObjectNode element = cell.putArray("elements").addObject();
        element.put("kind", "QR");
        element.put("hidden", false);
        element.put("payload", "https://pay.example.com");
        element.put("caption", "扫码结账");
        return document;
    }
}
