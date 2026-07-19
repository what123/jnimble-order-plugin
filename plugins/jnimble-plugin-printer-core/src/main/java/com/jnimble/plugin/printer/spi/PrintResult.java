package com.jnimble.plugin.printer.spi;

public record PrintResult(boolean success, String jobId, String message) {
}
