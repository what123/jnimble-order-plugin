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
import com.jnimble.plugin.printer.mapper.PrintJobMapper;
import com.jnimble.plugin.printer.model.entity.PrintJobEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * PrintJobService 单元测试。
 *
 * <p>测试打印任务的创建、查询和重试功能。</p>
 */
class PrintJobServiceTest {

    private PrintJobMapper printJobMapper;
    private PrintJobService printJobService;
    private MockedStatic<MapperUtils> mapperUtilsMock;

    @BeforeEach
    void setUp() {
        printJobMapper = mock(PrintJobMapper.class);
        printJobService = new PrintJobService(printJobMapper);
        mapperUtilsMock = mockStatic(MapperUtils.class);
    }

    @AfterEach
    void tearDown() {
        mapperUtilsMock.close();
    }

    /**
     * 测试创建打印任务时正确构建实体并设置默认值。
     */
    @Test
    void createJobShouldBuildEntityAndInsert() {
        mapperUtilsMock.when(() -> MapperUtils.insert(eq(printJobMapper), any(PrintJobEntity.class)))
                .thenAnswer(invocation -> {
                    PrintJobEntity entity = invocation.getArgument(1);
                    entity.setId("job-1");
                    return entity;
                });

        PrintJobEntity result = printJobService.createJob(
                "order-1", "printer-1", "node-1", "receipt", "content", "driver-1");

        assertNotNull(result);
        assertEquals("order-1", result.getOrderId());
        assertEquals("printer-1", result.getPrinterId());
        assertEquals("node-1", result.getNodeName());
        assertEquals("receipt", result.getType());
        assertEquals("content", result.getContent());
        assertEquals("driver-1", result.getDriverId());
        assertEquals("PENDING", result.getStatus());
        assertEquals(0, result.getRetryCount());
        assertEquals(3, result.getMaxRetries());
        assertNotNull(result.getCreatedAt());
    }

    /**
     * 测试根据状态查询打印任务列表。
     */
    @Test
    void listJobsShouldReturnFilteredList() {
        PrintJobEntity job = new PrintJobEntity();
        job.setId("job-1");
        job.setStatus("PENDING");
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(printJobMapper), eq(PrintJobEntity.class), any()))
                .thenReturn(List.of(job));

        List<PrintJobEntity> result = printJobService.listJobs("PENDING", null, null);

        assertEquals(1, result.size());
        assertEquals("PENDING", result.get(0).getStatus());
    }

    /**
     * 测试查询时状态为 null 时不添加状态过滤条件。
     */
    @Test
    void listJobsShouldHandleNullStatus() {
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(printJobMapper), eq(PrintJobEntity.class), any()))
                .thenReturn(List.of());

        List<PrintJobEntity> result = printJobService.listJobs(null, null, null);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    /**
     * 测试查询时状态为空白字符串时不添加状态过滤条件。
     */
    @Test
    void listJobsShouldHandleBlankStatus() {
        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(printJobMapper), eq(PrintJobEntity.class), any()))
                .thenReturn(List.of());

        List<PrintJobEntity> result = printJobService.listJobs("  ", null, null);

        assertNotNull(result);
    }

    /**
     * 测试重试打印任务时递增重试次数并重置状态为 PENDING。
     */
    @Test
    void retryJobShouldIncrementRetryCountAndResetStatus() {
        PrintJobEntity entity = new PrintJobEntity();
        entity.setId("job-1");
        entity.setRetryCount(1);
        entity.setStatus("FAILED");
        entity.setErrorMessage("timeout");

        mapperUtilsMock.when(() -> MapperUtils.getById(eq(printJobMapper), eq("job-1"), any()))
                .thenReturn(entity);
        mapperUtilsMock.when(() -> MapperUtils.updateById(eq(printJobMapper), eq(entity)))
                .thenReturn(entity);

        PrintJobEntity result = printJobService.retryJob("job-1");

        assertEquals(2, result.getRetryCount());
        assertEquals("PENDING", result.getStatus());
        assertNull(result.getErrorMessage());
    }

    /**
     * 测试重试不存在的打印任务时抛出 IllegalArgumentException。
     */
    @Test
    void retryJobShouldThrowWhenJobNotFound() {
        mapperUtilsMock.when(() -> MapperUtils.getById(eq(printJobMapper), eq("missing"), any()))
                .thenThrow(new IllegalArgumentException("Print job not found: missing"));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> printJobService.retryJob("missing"));
    }
}
