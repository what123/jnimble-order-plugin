package com.jnimble.plugin.order.table.controller;

import com.jnimble.plugin.order.table.model.entity.TableEntity;
import com.jnimble.plugin.order.table.service.TableService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableAdminControllerTest {

    @Mock
    private TableService tableService;

    @InjectMocks
    private TableAdminController tableAdminController;

    @Test
    void testTablesPage() {
        Map<String, Object> model = new HashMap<>();
        TableEntity table = new TableEntity();
        table.setId(1L);
        when(tableService.listTables(null)).thenReturn(List.of(table));

        String viewName = tableAdminController.tablesPage(model);

        assertEquals("plugin/order-table/admin/tables", viewName);
        assertEquals("order-tables", model.get("activeNav"));
        assertEquals(List.of(table), model.get("tables"));
    }

    @Test
    void testCreateTable() {
        doAnswer(invocation -> {
            TableEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            entity.setScanCode("TB00000001");
            return entity;
        }).when(tableService).createTable(any(TableEntity.class));

        Map<String, Object> result = tableAdminController.createTable(
                "大堂1号", "大厅", 2, 6, null, null
        );

        assertTrue((Boolean) result.get("success"));
        assertEquals(1L, result.get("id"));
        assertEquals("大堂1号", result.get("tableName"));
        assertEquals("TB00000001", result.get("scanCode"));
        verify(tableService).createTable(any(TableEntity.class));
    }

    @Test
    void testListTables() {
        TableEntity table = new TableEntity();
        table.setId(1L);
        when(tableService.listTables(null)).thenReturn(List.of(table));

        List<TableEntity> result = tableAdminController.listTables();

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}
