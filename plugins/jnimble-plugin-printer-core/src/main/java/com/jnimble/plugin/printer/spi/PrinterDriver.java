package com.jnimble.plugin.printer.spi;

public interface PrinterDriver {
    String driverId();
    String driverName();
    boolean supports(PrinterConfig config);
    PrintResult print(PrintTask task, PrinterConfig config);
    PrinterStatus queryStatus(PrinterConfig config);
}
