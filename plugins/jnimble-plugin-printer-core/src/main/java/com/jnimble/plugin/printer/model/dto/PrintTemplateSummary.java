package com.jnimble.plugin.printer.model.dto;

import java.time.LocalDateTime;

public record PrintTemplateSummary(
        String id,
        String code,
        String name,
        Integer paperWidthMm,
        String status,
        Integer draftRevision,
        Integer publishedVersion,
        LocalDateTime updatedAt
) {
}
