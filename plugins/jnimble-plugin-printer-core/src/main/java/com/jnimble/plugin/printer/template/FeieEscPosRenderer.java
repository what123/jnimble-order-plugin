package com.jnimble.plugin.printer.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 把 {@code jnimble.print-document.v1} JSON 渲染成飞鹅云 ESC/POS 文本。
 *
 * <p>飞鹅云支持的 ESC/POS 标签:</p>
 * <ul>
 *   <li>{@code <CB>...</CB>} 居中加粗</li>
 *   <li>{@code <BOLD>...</BOLD>} 加粗</li>
 *   <li>{@code <C double-height>}...{@code </C>} 居中放大</li>
 *   <li>{@code <QR>payload</QR>} 二维码</li>
 *   <li>{@code <BR>} 换行</li>
 *   <li>{@code <CENTER>...</CENTER>} 居中</li>
 * </ul>
 *
 * <p>本类只做格式转换,不发送 HTTP 请求。driver 拿到转换后的字符串再调飞鹅 API。</p>
 */
@Component
public class FeieEscPosRenderer {

    private static final String LINE_SEPARATOR = "\n";

    private final ObjectMapper objectMapper;

    public FeieEscPosRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 把渲染好的 document JSON 转成飞鹅 ESC/POS 文本。
     *
     * @param documentJson PrintTemplateRenderService 输出的 document JSON 字符串
     * @return 飞鹅云 {@code OpenPrintMsg.content} 字段值
     */
    public String render(String documentJson) {
        if (documentJson == null || documentJson.isBlank()) {
            return "";
        }
        try {
            JsonNode document = objectMapper.readTree(documentJson);
            return renderDocument(document);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid print document JSON: " + ex.getMessage(), ex);
        }
    }

    private String renderDocument(JsonNode document) {
        StringBuilder output = new StringBuilder();
        int paperWidthMm = document.path("paper").path("widthMm").asInt(58);
        int columnWidth = paperWidthChars(paperWidthMm);

        JsonNode rows = document.path("rows");
        if (!rows.isArray()) {
            return output.toString();
        }
        for (JsonNode row : rows) {
            JsonNode cells = row.path("cells");
            if (!cells.isArray()) {
                continue;
            }
            for (JsonNode cell : cells) {
                JsonNode elements = cell.path("elements");
                if (!elements.isArray()) {
                    continue;
                }
                for (JsonNode element : elements) {
                    renderElement(element, columnWidth, output);
                }
            }
            output.append(LINE_SEPARATOR);
        }
        return output.toString().trim() + LINE_SEPARATOR;
    }

    private void renderElement(JsonNode element, int columnWidth, StringBuilder output) {
        String kind = element.path("kind").asText("");
        switch (kind) {
            case "TEXT" -> renderText(element, output);
            case "TABLE" -> renderTable(element, columnWidth, output);
            case "AMOUNT_ROWS" -> renderAmountRows(element, columnWidth, output);
            case "QR" -> renderQr(element, output);
            default -> {
                // 未知块类型忽略,避免驱动因扩展类型崩溃
            }
        }
    }

    private void renderText(JsonNode element, StringBuilder output) {
        String text = element.path("text").asText("");
        if (text.isEmpty()) {
            return;
        }
        String align = element.path("align").asText("LEFT");
        boolean bold = element.path("bold").asBoolean(false);
        String size = element.path("size").asText("NORMAL");

        String wrapped;
        if ("LARGE".equals(size)) {
            wrapped = "<C double-height>" + escape(text) + "</C>";
        } else if (bold) {
            wrapped = "<BOLD>" + escape(text) + "</BOLD>";
        } else if ("CENTER".equalsIgnoreCase(align)) {
            wrapped = "<CENTER>" + escape(text) + "</CENTER>";
        } else {
            wrapped = escape(text);
        }
        output.append(wrapped).append(LINE_SEPARATOR);
    }

    private void renderTable(JsonNode element, int columnWidth, StringBuilder output) {
        boolean showHeader = element.path("showHeader").asBoolean(true);
        boolean bold = element.path("bold").asBoolean(false);
        JsonNode columns = element.path("columns");
        JsonNode rows = element.path("rows");
        if (!columns.isArray() || columns.isEmpty()) {
            return;
        }

        List<Integer> widths = computeColumnWidths(columns, columnWidth);
        List<String> headers = new ArrayList<>();
        for (JsonNode column : columns) {
            headers.add(column.path("title").asText(""));
        }

        if (showHeader) {
            output.append(formatTableRow(headers, widths, bold)).append(LINE_SEPARATOR);
            output.append(formatSeparator(widths)).append(LINE_SEPARATOR);
        }
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                List<String> cells = new ArrayList<>();
                for (JsonNode column : columns) {
                    String field = column.path("field").asText();
                    cells.add(row.path(field).path("text").asText(""));
                }
                output.append(formatTableRow(cells, widths, false)).append(LINE_SEPARATOR);
            }
        }
    }

    private void renderAmountRows(JsonNode element, int columnWidth, StringBuilder output) {
        boolean bold = element.path("bold").asBoolean(true);
        JsonNode rows = element.path("rows");
        if (!rows.isArray()) {
            return;
        }
        for (JsonNode row : rows) {
            String label = row.path("label").asText("");
            String value = row.path("value").asText("");
            String line = padJustify(label, value, columnWidth);
            if (bold) {
                output.append("<BOLD>").append(line).append("</BOLD>");
            } else {
                output.append(line);
            }
            output.append(LINE_SEPARATOR);
        }
    }

    private void renderQr(JsonNode element, StringBuilder output) {
        boolean hidden = element.path("hidden").asBoolean(false);
        if (hidden) {
            return;
        }
        String payload = element.path("payload").asText("");
        if (payload.isEmpty()) {
            return;
        }
        output.append("<QR>").append(escape(payload)).append("</QR>").append(LINE_SEPARATOR);
        String caption = element.path("caption").asText("");
        if (!caption.isEmpty()) {
            output.append("<CENTER>").append(escape(caption)).append("</CENTER>").append(LINE_SEPARATOR);
        }
    }

    private int paperWidthChars(int paperWidthMm) {
        // 58mm 纸约 32 字符,80mm 纸约 48 字符
        if (paperWidthMm >= 80) {
            return 48;
        }
        return 32;
    }

    private List<Integer> computeColumnWidths(JsonNode columns, int totalWidth) {
        List<Integer> widths = new ArrayList<>();
        int totalBasis = 0;
        for (JsonNode column : columns) {
            totalBasis += column.path("widthBasisPoints").asInt(0);
        }
        if (totalBasis <= 0) {
            int each = totalWidth / columns.size();
            for (int i = 0; i < columns.size(); i++) {
                widths.add(each);
            }
            return widths;
        }
        int remaining = totalWidth;
        for (int i = 0; i < columns.size(); i++) {
            int basis = columns.get(i).path("widthBasisPoints").asInt(0);
            int width = i == columns.size() - 1
                    ? remaining
                    : Math.max(1, totalWidth * basis / totalBasis);
            widths.add(width);
            remaining -= width;
        }
        return widths;
    }

    private String formatTableRow(List<String> cells, List<Integer> widths, boolean bold) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            int width = widths.get(i);
            String cell = truncateOrPad(cells.get(i), width);
            row.append(cell);
            if (i < cells.size() - 1) {
                row.append(" ");
            }
        }
        if (bold) {
            return "<BOLD>" + row + "</BOLD>";
        }
        return row.toString();
    }

    private String formatSeparator(List<Integer> widths) {
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < widths.size(); i++) {
            separator.append("-".repeat(Math.max(1, widths.get(i))));
            if (i < widths.size() - 1) {
                separator.append(" ");
            }
        }
        return separator.toString();
    }

    private String truncateOrPad(String text, int width) {
        if (text.length() > width) {
            return text.substring(0, Math.max(0, width - 1)) + "…";
        }
        return text + " ".repeat(width - text.length());
    }

    private String padJustify(String left, String right, int totalWidth) {
        int space = Math.max(1, totalWidth - displayWidth(left) - displayWidth(right));
        return left + " ".repeat(space) + right;
    }

    private int displayWidth(String text) {
        // 简化:中文按 2 字符宽度估算
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            width += (c > 0x7F) ? 2 : 1;
        }
        return width;
    }

    private String escape(String text) {
        return text.replace("<", "&lt;").replace(">", "&gt;");
    }
}
