package com.jnimble.plugin.printer.spi;

/**
 * 打印机某天订单统计结果。
 *
 * @param supported driver 是否支持该统计
 * @param printed   已打印订单数,-1 表示不支持
 * @param waiting   等待打印订单数,-1 表示不支持
 * @param message   原始消息或错误描述
 */
public record PrinterOrderStatistics(
        boolean supported,
        long printed,
        long waiting,
        String message
) {
    public static PrinterOrderStatistics of(long printed, long waiting, String message) {
        return new PrinterOrderStatistics(true, printed, waiting, message);
    }

    public static PrinterOrderStatistics unsupported(String driverId) {
        return new PrinterOrderStatistics(false, -1, -1, "Order statistics not supported by driver: " + driverId);
    }
}
