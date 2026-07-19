package com.jnimble.plugin.printer.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import com.jnimble.sdk.hook.RegistrationHandle;

@Component
public class PrinterDriverRegistry {

    private final Map<String, PrinterDriver> drivers = new ConcurrentHashMap<>();

    public RegistrationHandle register(PrinterDriver driver) {
        drivers.put(driver.driverId(), driver);
        return () -> drivers.remove(driver.driverId(), driver);
    }

    public PrinterDriver resolve(String driverId) {
        PrinterDriver driver = drivers.get(driverId);
        if (driver == null) {
            throw new IllegalArgumentException("No driver found: " + driverId);
        }
        return driver;
    }

    public Collection<PrinterDriver> allDrivers() {
        return Collections.unmodifiableCollection(drivers.values());
    }
}
