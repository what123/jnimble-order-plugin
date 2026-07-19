package com.jnimble.plugin.order.table.controller;

import com.jnimble.plugin.order.table.model.entity.TableEntity;
import com.jnimble.plugin.order.table.service.TableService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin/plugins/order-table")
public class TableAdminController {

    private final TableService tableService;

    public TableAdminController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/tables")
    public String tablesPage(Map<String, Object> model) {
        model.put("tables", tableService.listTables(null));
        model.put("activeNav", "order-tables");
        return "plugin/order-table/admin/tables";
    }

    @PostMapping("/tables")
    @ResponseBody
    public Map<String, Object> createTable(
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Integer minPartySize,
            @RequestParam(required = false) Integer maxPartySize,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer seatCount
    ) {
        TableEntity entity = new TableEntity();
        entity.setTableName(tableName);
        entity.setCode(code);
        entity.setArea(area);
        entity.setMinPartySize(minPartySize);
        entity.setMaxPartySize(maxPartySize);
        entity.setSeatCount(seatCount);
        entity.setStatus("FREE");
        tableService.createTable(entity);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("id", entity.getId());
        result.put("tableName", entity.getTableName());
        result.put("scanCode", entity.getScanCode());
        return result;
    }

    /**
     * Compatibility entry point for callers that still submit the legacy code and seat count fields.
     */
    public Map<String, Object> createTable(String code, String area, Integer seatCount) {
        return createTable(code, area, 1, seatCount, code, seatCount);
    }

    @GetMapping("/tables/list")
    @ResponseBody
    public List<TableEntity> listTables() {
        return tableService.listTables(null);
    }
}
