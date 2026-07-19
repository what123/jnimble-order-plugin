package com.jnimble.plugin.printer.kitchen;

import com.jnimble.plugin.order.kitchen.KitchenDispatchMode;
import com.jnimble.plugin.order.kitchen.KitchenDispatchModeHook;
import com.jnimble.plugin.order.kitchen.KitchenTicketNumberContext;
import com.jnimble.plugin.printer.model.entity.PrintNodeEntity;
import com.jnimble.plugin.printer.service.PrintNodeService;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class PrinterKitchenDispatchModeHook implements KitchenDispatchModeHook {

    private final PrintNodeService printNodeService;

    public PrinterKitchenDispatchModeHook(PrintNodeService printNodeService) {
        this.printNodeService = printNodeService;
    }

    @Override
    public Optional<KitchenDispatchMode> resolve(KitchenTicketNumberContext context) {
        PrintNodeEntity node = printNodeService.getNodeByName("order.confirmed");
        if (node == null || !Boolean.TRUE.equals(node.getEnabled())) {
            return Optional.empty();
        }
        return Optional.of("PRINT".equalsIgnoreCase(node.getOperationMode())
                ? KitchenDispatchMode.PRINT
                : KitchenDispatchMode.PAPERLESS);
    }
}

