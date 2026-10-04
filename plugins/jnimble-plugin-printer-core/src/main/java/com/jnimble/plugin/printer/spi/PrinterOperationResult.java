package com.jnimble.plugin.printer.spi;

/**
 * driver 操作结果(非打印类操作,如清空队列、修改打印机)。
 */
public record PrinterOperationResult(
        boolean success,
        String driverId,
        String message
) {
    public static PrinterOperationResult success(String driverId, String message) {
        return new PrinterOperationResult(true, driverId, message);
    }

    public static PrinterOperationResult failure(String driverId, String message) {
        return new PrinterOperationResult(false, driverId, message);
    }

    public static PrinterOperationResult unsupported(String driverId) {
        return new PrinterOperationResult(false, driverId, "Operation not supported by driver: " + driverId);
    }
}
