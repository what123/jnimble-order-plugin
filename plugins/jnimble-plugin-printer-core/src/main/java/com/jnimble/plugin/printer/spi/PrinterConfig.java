package com.jnimble.plugin.printer.spi;

import java.util.Map;

public record PrinterConfig(String driverId, Map<String, String> properties) {
}
