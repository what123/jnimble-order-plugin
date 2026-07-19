package com.jnimble.plugin.printer.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PrintTemplatePreviewRequest(
        Integer paperWidthMm,
        JsonNode definition,
        JsonNode data
) {
}
