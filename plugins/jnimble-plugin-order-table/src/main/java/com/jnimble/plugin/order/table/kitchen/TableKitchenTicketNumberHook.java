package com.jnimble.plugin.order.table.kitchen;

import com.jnimble.plugin.order.kitchen.KitchenTicketNumberContext;
import com.jnimble.plugin.order.kitchen.KitchenTicketNumberHook;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import com.jnimble.plugin.order.table.service.TableService;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class TableKitchenTicketNumberHook implements KitchenTicketNumberHook {

    private final TableService tableService;

    public TableKitchenTicketNumberHook(TableService tableService) {
        this.tableService = tableService;
    }

    @Override
    public Optional<String> resolve(KitchenTicketNumberContext context) {
        if (context == null || context.tableId() == null) {
            return Optional.empty();
        }
        try {
            TableEntity table = tableService.getTable(context.tableId());
            String value = firstNonBlank(table.getTableName(), table.getCode());
            return Optional.ofNullable(value);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }
}

