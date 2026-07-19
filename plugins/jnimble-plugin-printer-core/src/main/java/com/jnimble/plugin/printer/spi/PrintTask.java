package com.jnimble.plugin.printer.spi;

public record PrintTask(
        String orderId,
        String content,
        String contentType,
        String contentEncoding,
        String type,
        int copies
) {

    public PrintTask(String orderId, String content, String type, int copies) {
        this(orderId, content, "text/plain; charset=UTF-8", "PLAIN", type, copies);
    }
}
