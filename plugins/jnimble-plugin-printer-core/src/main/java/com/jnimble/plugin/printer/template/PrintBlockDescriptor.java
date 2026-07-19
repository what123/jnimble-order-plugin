package com.jnimble.plugin.printer.template;

import java.util.Map;

public record PrintBlockDescriptor(
        String type,
        String provider,
        int version,
        String label,
        String description,
        int defaultWidthBasisPoints,
        int minWidthBasisPoints,
        Map<String, Object> defaultConfig
) {
}
