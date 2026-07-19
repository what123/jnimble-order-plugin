package com.jnimble.plugin.printer.spi;

import com.fasterxml.jackson.databind.JsonNode;

public interface PrinterPayloadConverter {

    String driverId();

    EncodedPrintPayload convert(JsonNode printDocument, PrinterConfig config);
}
