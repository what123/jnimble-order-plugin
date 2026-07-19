package com.jnimble.plugin.printer.spi;

public record EncodedPrintPayload(
        String contentType,
        String contentEncoding,
        String content
) {
}
