package com.jnimble.plugin.printer.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record PrintTemplatePreviewResponse(
        JsonNode document,
        String documentJson,
        List<PrintTemplateViolation> violations
) {
}
