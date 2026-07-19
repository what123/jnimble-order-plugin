package com.jnimble.plugin.printer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.printer.mapper.PrintNodeMapper;
import com.jnimble.plugin.printer.model.entity.PrintNodeEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * PrintNodeService 单元测试。
 *
 * <p>测试打印节点的 CRUD 操作，使用 MockedStatic 模拟 MapperUtils 静态方法。</p>
 */
class PrintNodeServiceTest {

    private PrintNodeMapper printNodeMapper;
    private PrintNodeService printNodeService;
    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        printNodeMapper = mock(PrintNodeMapper.class);
        printNodeService = new PrintNodeService(printNodeMapper);
        mapperUtilsMock = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    /**
     * 测试列出所有节点返回正确的列表。
     */
    @Test
    void listNodesShouldReturnNodeList() {
        PrintNodeEntity entity = createPrintNodeEntity("n1", "Node 1");
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(printNodeMapper), eq(PrintNodeEntity.class), any()))
                .thenReturn(List.of(entity));

        List<PrintNodeEntity> result = printNodeService.listNodes();

        assertEquals(1, result.size());
        assertEquals("Node 1", result.get(0).getNodeName());
    }

    /**
     * 测试列出已启用的节点返回正确的列表。
     */
    @Test
    void listEnabledNodesShouldReturnEnabledNodes() {
        PrintNodeEntity entity = createPrintNodeEntity("n1", "Node 1");
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(printNodeMapper), eq(PrintNodeEntity.class), any()))
                .thenReturn(List.of(entity));

        List<PrintNodeEntity> result = printNodeService.listEnabledNodes();

        assertEquals(1, result.size());
    }

    /**
     * 测试根据节点名称获取节点返回正确的实体。
     */
    @Test
    void getNodeByNameShouldReturnEntity() {
        PrintNodeEntity entity = createPrintNodeEntity("n1", "Node 1");
        mapperUtilsMock.when(() -> MapperUtils.selectOne(eq(printNodeMapper), eq(PrintNodeEntity.class), any()))
                .thenReturn(entity);

        PrintNodeEntity result = printNodeService.getNodeByName("Node 1");

        assertNotNull(result);
        assertEquals("n1", result.getId());
    }

    /**
     * 测试根据节点名称获取节点未找到时返回 null。
     */
    @Test
    void getNodeByNameShouldReturnNullWhenNotFound() {
        mapperUtilsMock.when(() -> MapperUtils.selectOne(eq(printNodeMapper), eq(PrintNodeEntity.class), any()))
                .thenReturn(null);

        PrintNodeEntity result = printNodeService.getNodeByName("NonExistent");

        assertNull(result);
    }

    /**
     * 测试创建节点时调用插入操作并返回实体。
     */
    @Test
    void createNodeShouldInsertEntity() {
        PrintNodeEntity entity = createPrintNodeEntity("n2", "Node 2");
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(printNodeMapper), eq(entity)))
                .thenReturn(entity);

        PrintNodeEntity result = printNodeService.createNode(entity);

        assertNotNull(result);
        assertEquals("Node 2", result.getNodeName());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }

    /**
     * 测试更新节点时调用 updateById 操作并设置更新时间。
     */
    @Test
    void updateNodeShouldCallUpdateById() {
        PrintNodeEntity entity = createPrintNodeEntity("n1", "Updated");
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(printNodeMapper), eq(entity)))
                .thenReturn(entity);

        PrintNodeEntity result = printNodeService.updateNode(entity);

        assertEquals("Updated", result.getNodeName());
        assertNotNull(result.getUpdatedAt());
    }

    /**
     * 测试删除节点时调用 deleteById 操作。
     */
    @Test
    void deleteNodeShouldCallDeleteById() {
        mapperUtilsMock.when(() -> MapperUtils.deleteById(eq(printNodeMapper), eq("n1")))
                .thenReturn(1);

        printNodeService.deleteNode("n1");

        mapperUtilsMock.verify(() -> MapperUtils.deleteById(printNodeMapper, "n1"));
    }

    private PrintNodeEntity createPrintNodeEntity(String id, String nodeName) {
        PrintNodeEntity entity = new PrintNodeEntity();
        entity.setId(id);
        entity.setNodeName(nodeName);
        return entity;
    }
}
