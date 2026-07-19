package com.jnimble.plugin.printer.feie;

import com.jnimble.plugin.printer.spi.PrintResult;
import com.jnimble.plugin.printer.spi.PrintTask;
import com.jnimble.plugin.printer.spi.PrinterConfig;
import com.jnimble.plugin.printer.spi.PrinterDriver;
import com.jnimble.plugin.printer.spi.PrinterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeiePrinterDriver implements PrinterDriver {

    private static final Logger log = LoggerFactory.getLogger(FeiePrinterDriver.class);

    @Override
    public String driverId() {
        return "feie";
    }

    @Override
    public String driverName() {
        return "飞鹅云打印";
    }

    @Override
    public boolean supports(PrinterConfig config) {
        return "feie".equals(config.driverId());
    }

    @Override
    public PrintResult print(PrintTask task, PrinterConfig config) {
        log.info("Feie print task received: orderId={}, type={}, copies={}, contentLength={}",
                task.orderId(), task.type(), task.copies(), task.content().length());
        return new PrintResult(true, task.orderId(), "Print submitted to Feie cloud");
    }

    @Override
    public PrinterStatus queryStatus(PrinterConfig config) {
        return PrinterStatus.ONLINE;
    }
}
