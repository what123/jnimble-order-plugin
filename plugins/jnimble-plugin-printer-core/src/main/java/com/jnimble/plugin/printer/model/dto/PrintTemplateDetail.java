package com.jnimble.plugin.printer.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record PrintTemplateDetail(
        String id,
        String code,
        String name,
        Integer paperWidthMm,
        String status,
        Integer draftRevision,
        Integer publishedVersion,
        JsonNode definition,
        String definitionJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
