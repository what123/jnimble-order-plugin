package com.jnimble.plugin.payment.controller;

import com.jnimble.plugin.payment.model.entity.PaymentEntity;
import com.jnimble.plugin.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 支付管理控制器单元测试。
 * 测试支付记录页面展示功能。
 */
@ExtendWith(MockitoExtension.class)
class PaymentAdminControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentAdminController paymentAdminController;

    private Map<String, Object> model;

    @BeforeEach
    void setUp() {
        model = new HashMap<>();
    }

    /**
     * 测试支付记录页面：验证正确调用Service并返回视图名称。
     */
    @Test
    void testRecordsPage() {
        // 准备测试数据
        PaymentEntity payment = new PaymentEntity();
        payment.setId("pay001");
        payment.setOrderId("ORD-001");
        payment.setMethod("CASH");
        payment.setAmount(new BigDecimal("100.00"));

        when(paymentService.listRecent(100)).thenReturn(List.of(payment));

        // 执行测试
        String viewName = paymentAdminController.recordsPage(model);

        // 验证结果
        assertEquals("plugin/payment/admin/records", viewName);
        assertEquals(List.of(payment), model.get("records"));

        // 验证Service调用
        verify(paymentService).listRecent(100);
    }

    /**
     * 测试支付记录页面：验证无记录时返回空列表。
     */
    @Test
    void testRecordsPageEmpty() {
        // 模拟Service返回空列表
        when(paymentService.listRecent(100)).thenReturn(List.of());

        // 执行测试
        String viewName = paymentAdminController.recordsPage(model);

        // 验证结果
        assertEquals("plugin/payment/admin/records", viewName);
        assertNotNull(model.get("records"));
        assertTrue(((List<?>) model.get("records")).isEmpty());

        // 验证Service调用
        verify(paymentService).listRecent(100);
    }

    /**
     * 测试支付记录页面：验证多条记录全部传递给视图。
     */
    @Test
    void testRecordsPageMultipleRecords() {
        // 准备测试数据
        PaymentEntity payment1 = new PaymentEntity();
        payment1.setId("pay001");
        payment1.setAmount(new BigDecimal("50.00"));

        PaymentEntity payment2 = new PaymentEntity();
        payment2.setId("pay002");
        payment2.setAmount(new BigDecimal("80.00"));

        PaymentEntity payment3 = new PaymentEntity();
        payment3.setId("pay003");
        payment3.setAmount(new BigDecimal("120.00"));

        when(paymentService.listRecent(100)).thenReturn(List.of(payment1, payment2, payment3));

        // 执行测试
        String viewName = paymentAdminController.recordsPage(model);

        // 验证结果
        assertEquals("plugin/payment/admin/records", viewName);
        @SuppressWarnings("unchecked")
        List<PaymentEntity> records = (List<PaymentEntity>) model.get("records");
        assertEquals(3, records.size());

        // 验证Service调用
        verify(paymentService).listRecent(100);
    }
}
