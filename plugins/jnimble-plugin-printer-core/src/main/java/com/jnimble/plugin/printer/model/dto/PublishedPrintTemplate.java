package com.jnimble.plugin.printer.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PublishedPrintTemplate(
        String templateId,
        int version,
        int paperWidthMm,
        JsonNode definition
) {
}

