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
import com.jnimble.plugin.printer.mapper.PrinterMapper;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * PrinterService 单元测试。
 *
 * <p>测试打印机的 CRUD 操作，使用 MockedStatic 模拟 MapperUtils 静态方法。</p>
 */
class PrinterServiceTest {

    private PrinterMapper printerMapper;
    private PrinterService printerService;
    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        printerMapper = mock(PrinterMapper.class);
        printerService = new PrinterService(printerMapper);
        mapperUtilsMock = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    /**
     * 测试列出所有打印机返回正确的列表。
     */
    @Test
    void listPrintersShouldReturnPrinterList() {
        PrinterEntity entity = createPrinterEntity("p1", "Printer 1");
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(printerMapper), eq(PrinterEntity.class), isNull()))
                .thenReturn(List.of(entity));

        List<PrinterEntity> result = printerService.listPrinters();

        assertEquals(1, result.size());
        assertEquals("Printer 1", result.get(0).getName());
    }

    /**
     * 测试根据ID获取打印机返回正确的实体。
     */
    @Test
    void getPrinterShouldReturnEntity() {
        PrinterEntity entity = createPrinterEntity("p1", "Printer 1");
        mapperUtilsMock.when(() -> MapperUtils.getById(eq(printerMapper), eq("p1"), isNull()))
                .thenReturn(entity);

        PrinterEntity result = printerService.getPrinter("p1");

        assertNotNull(result);
        assertEquals("p1", result.getId());
    }

    /**
     * 测试创建打印机时调用插入操作并返回实体。
     */
    @Test
    void createPrinterShouldInsertEntity() {
        PrinterEntity entity = createPrinterEntity("p2", "Printer 2");
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(printerMapper), eq(entity)))
                .thenReturn(entity);

        PrinterEntity result = printerService.createPrinter(entity);

        assertNotNull(result);
        assertEquals("Printer 2", result.getName());
    }

    /**
     * 测试更新打印机时调用 updateById 操作。
     */
    @Test
    void updatePrinterShouldCallUpdateById() {
        PrinterEntity entity = createPrinterEntity("p1", "Updated");
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(printerMapper), eq(entity)))
                .thenReturn(entity);

        PrinterEntity result = printerService.updatePrinter(entity);

        assertEquals("Updated", result.getName());
    }

    /**
     * 测试删除打印机时调用 deleteById 操作。
     */
    @Test
    void deletePrinterShouldCallDeleteById() {
        mapperUtilsMock.when(() -> MapperUtils.deleteById(eq(printerMapper), eq("p1")))
                .thenReturn(1);

        printerService.deletePrinter("p1");

        mapperUtilsMock.verify(() -> MapperUtils.deleteById(printerMapper, "p1"));
    }

    private PrinterEntity createPrinterEntity(String id, String name) {
        PrinterEntity entity = new PrinterEntity();
        entity.setId(id);
        entity.setName(name);
        return entity;
    }
}
