package com.jnimble.plugin.printer.spi;

/**
 * 打印机 driver SPI。
 *
 * <p>核心能力是 {@link #print} 和 {@link #queryStatus}。其他能力以 default 方法提供,
 * driver 按需 override,未实现的返回空 {@link PrinterOperationResult#unsupported()}。</p>
 */
public interface PrinterDriver {
    String driverId();
    String driverName();
    boolean supports(PrinterConfig config);
    PrintResult print(PrintTask task, PrinterConfig config);
    PrinterStatus queryStatus(PrinterConfig config);

    /**
     * 清空打印机远程待打印队列(云端尚未下发的任务)。
     * 默认不支持,driver 按需 override。
     */
    default PrinterOperationResult clearPendingQueue(PrinterConfig config) {
        return PrinterOperationResult.unsupported(driverId());
    }

    /**
     * 查询指定打印机某天的订单统计。
     * 默认不支持,driver 按需 override。
     *
     * @param date 格式 {@code yyyy-MM-dd}
     */
    default PrinterOrderStatistics queryOrderStatistics(PrinterConfig config, String date) {
        return PrinterOrderStatistics.unsupported(driverId());
    }
}
