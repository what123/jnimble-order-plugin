package com.jnimble.plugin.printer.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PrintTemplateSaveRequest(
        String code,
        String name,
        Integer paperWidthMm,
        Integer draftRevision,
        JsonNode definition
) {
}
