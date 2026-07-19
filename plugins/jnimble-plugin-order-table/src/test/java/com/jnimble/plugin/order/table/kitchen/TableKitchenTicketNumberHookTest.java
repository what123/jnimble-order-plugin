package com.jnimble.plugin.order.table.kitchen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.jnimble.plugin.order.kitchen.KitchenTicketNumberContext;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import com.jnimble.plugin.order.table.service.TableService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TableKitchenTicketNumberHookTest {

    @Mock
    private TableService tableService;

    @Test
    void resolvesConfiguredTableName() {
        TableEntity table = new TableEntity();
        table.setId(8L);
        table.setTableName("大堂1号");
        when(tableService.getTable(8L)).thenReturn(table);
        TableKitchenTicketNumberHook hook = new TableKitchenTicketNumberHook(tableService);

        var value = hook.resolve(new KitchenTicketNumberContext(1L, "D001", 8L, 20L, "TABLE"));

        assertEquals("大堂1号", value.orElseThrow());
    }

    @Test
    void ignoresOrdersWithoutTable() {
        TableKitchenTicketNumberHook hook = new TableKitchenTicketNumberHook(tableService);

        assertTrue(hook.resolve(new KitchenTicketNumberContext(1L, "D001", null, null, "NUMBER")).isEmpty());
    }
}

