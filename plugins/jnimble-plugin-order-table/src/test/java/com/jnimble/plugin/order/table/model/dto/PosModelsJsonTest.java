package com.jnimble.plugin.order.table.model.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.table.model.dto.PosModels.SessionDetail;
import com.jnimble.plugin.order.table.model.dto.PosModels.TableBoardItem;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosModelsJsonTest {

    private static final long LARGE_ID = 2_076_973_284_358_688_770L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesIdsAsStringsWithoutLosingJavaScriptPrecision() throws Exception {
        TableEntity table = new TableEntity();
        table.setId(LARGE_ID);
        OrderEntity order = new OrderEntity();
        order.setId(LARGE_ID - 1);
        order.setTableId(LARGE_ID);
        OrderItemEntity item = new OrderItemEntity();
        item.setId(LARGE_ID - 2);
        item.setOrderId(LARGE_ID - 1);

        JsonNode board = objectMapper.readTree(objectMapper.writeValueAsString(
                new TableBoardItem(
                        LARGE_ID, "大堂1号", "大厅", 2, 6, "TB00000001",
                        "FREE", 0, 6, List.of()
                )
        ));
        JsonNode session = objectMapper.readTree(objectMapper.writeValueAsString(
                new SessionDetail(null, table, List.of(table), order, List.of(item))
        ));

        assertTrue(board.path("tableId").isTextual());
        assertEquals(Long.toString(LARGE_ID), board.path("tableId").asText());
        assertEquals(Long.toString(LARGE_ID), session.path("primaryTable").path("id").asText());
        assertEquals(Long.toString(LARGE_ID - 1), session.path("order").path("id").asText());
        assertEquals(Long.toString(LARGE_ID - 2), session.path("items").path(0).path("id").asText());
    }

    @Test
    void databaseGeneratedEntitiesUseAutoIncrementIds() throws Exception {
        assertAutoId(TableEntity.class);
        assertAutoId(OrderEntity.class);
        assertAutoId(OrderItemEntity.class);
    }

    private void assertAutoId(Class<?> entityType) throws Exception {
        TableId tableId = entityType.getDeclaredField("id").getAnnotation(TableId.class);
        assertEquals(IdType.AUTO, tableId.type(), entityType.getSimpleName());
    }
}
