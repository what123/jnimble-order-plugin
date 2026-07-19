package com.jnimble.plugin.order.table.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.table.mapper.TableMapper;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 餐桌服务单元测试。
 * 测试餐桌列表查询、创建、更新、删除以及状态更新等核心功能。
 */
@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    @Mock
    private TableMapper tableMapper;

    @InjectMocks
    private TableService tableService;

    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        mapperUtilsMock = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    /**
     * 测试按区域查询餐桌列表：验证返回指定区域的餐桌。
     */
    @Test
    void testListTablesByArea() {
        // 准备测试数据
        String area = "VIP";
        TableEntity table1 = new TableEntity();
        table1.setId(1L);
        table1.setCode("V01");
        table1.setArea(area);
        TableEntity table2 = new TableEntity();
        table2.setId(2L);
        table2.setCode("V02");
        table2.setArea(area);

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(tableMapper), eq(TableEntity.class), any()))
                .thenReturn(Arrays.asList(table1, table2));

        // 执行测试
        List<TableEntity> result = tableService.listTables(area);

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(area, result.get(0).getArea());
        assertEquals(area, result.get(1).getArea());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.selectList(eq(tableMapper), eq(TableEntity.class), any()));
    }

    /**
     * 测试查询所有餐桌列表：验证未指定区域时返回所有餐桌。
     */
    @Test
    void testListAllTables() {
        // 准备测试数据
        TableEntity table1 = new TableEntity();
        table1.setId(1L);
        table1.setCode("A01");
        TableEntity table2 = new TableEntity();
        table2.setId(2L);
        table2.setCode("A02");

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(tableMapper), eq(TableEntity.class), any()))
                .thenReturn(Arrays.asList(table1, table2));

        // 执行测试
        List<TableEntity> result = tableService.listTables(null);

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.size());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.selectList(eq(tableMapper), eq(TableEntity.class), any()));
    }

    /**
     * 测试根据ID查询餐桌：验证返回正确的餐桌信息。
     */
    @Test
    void testGetTable() {
        // 准备测试数据
        Long id = 1L;
        TableEntity expectedTable = new TableEntity();
        expectedTable.setId(id);
        expectedTable.setCode("A01");
        expectedTable.setStatus("FREE");

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.getById(eq(tableMapper), eq(id), any()))
                .thenReturn(expectedTable);

        // 执行测试
        TableEntity result = tableService.getTable(id);

        // 验证结果
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("A01", result.getCode());
        assertEquals("FREE", result.getStatus());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.getById(eq(tableMapper), eq(id), any()));
    }

    /**
     * 测试创建餐桌：验证餐桌创建时自动设置时间戳和默认状态。
     */
    @Test
    void testCreateTable() {
        // 准备测试数据
        TableEntity entity = new TableEntity();
        entity.setTableName("大堂1号");
        entity.setArea("大厅");
        entity.setMinPartySize(2);
        entity.setMaxPartySize(6);

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(tableMapper), any(TableEntity.class)))
                .thenAnswer(invocation -> {
                    TableEntity insertedEntity = invocation.getArgument(1);
                    insertedEntity.setId(1L);
                    return insertedEntity;
                });

        // 执行测试
        TableEntity result = tableService.createTable(entity);

        // 验证结果
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("大堂1号", result.getTableName());
        assertEquals("大堂1号", result.getCode());
        assertEquals(2, result.getMinPartySize());
        assertEquals(6, result.getMaxPartySize());
        assertEquals(6, result.getSeatCount());
        assertEquals("TB00000001", result.getScanCode());
        assertEquals("FREE", result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.insert(eq(tableMapper), any(TableEntity.class)));
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(tableMapper), any(TableEntity.class)));
    }

    @Test
    void testCreateTableRejectsInvalidPartySizeRange() {
        TableEntity entity = new TableEntity();
        entity.setTableName("大堂1号");
        entity.setMinPartySize(6);
        entity.setMaxPartySize(2);

        assertThrows(IllegalArgumentException.class, () -> tableService.createTable(entity));

        mapperUtilsMock.verify(
                () -> MapperUtils.insert(eq(tableMapper), any(TableEntity.class)),
                never()
        );
    }

    /**
     * 测试更新餐桌：验证餐桌更新时自动更新时间戳。
     */
    @Test
    void testUpdateTable() {
        // 准备测试数据
        TableEntity entity = new TableEntity();
        entity.setId(1L);
        entity.setCode("A01");
        entity.setArea("大厅");
        entity.setSeatCount(6);

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(tableMapper), any(TableEntity.class)))
                .thenReturn(entity);

        // 执行测试
        TableEntity result = tableService.updateTable(entity);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getUpdatedAt());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(tableMapper), any(TableEntity.class)));
    }

    /**
     * 测试删除餐桌：验证餐桌删除操作正确执行。
     */
    @Test
    void testDeleteTable() {
        // 准备测试数据
        Long id = 1L;

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.deleteById(eq(tableMapper), eq(id)))
                .thenReturn(1);

        // 执行测试
        tableService.deleteTable(id);

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.deleteById(eq(tableMapper), eq(id)));
    }

    /**
     * 测试更新餐桌状态：验证状态更新时自动更新时间戳。
     */
    @Test
    void testUpdateStatus() {
        // 准备测试数据
        Long id = 1L;
        String status = "OCCUPIED";

        // 模拟MapperUtils行为
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(tableMapper), any(TableEntity.class)))
                .thenAnswer(invocation -> {
                    TableEntity entity = invocation.getArgument(1);
                    return entity;
                });

        // 执行测试
        TableEntity result = tableService.updateStatus(id, status);

        // 验证结果
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals(status, result.getStatus());
        assertNotNull(result.getUpdatedAt());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.updateById(eq(tableMapper), any(TableEntity.class)));
    }
}
