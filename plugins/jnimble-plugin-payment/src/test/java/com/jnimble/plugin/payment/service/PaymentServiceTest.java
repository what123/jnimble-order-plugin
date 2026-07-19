package com.jnimble.plugin.payment.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.payment.mapper.PaymentMapper;
import com.jnimble.plugin.payment.model.entity.PaymentEntity;
import java.math.BigDecimal;
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
 * 支付服务单元测试。
 * 测试支付处理、按订单查询支付记录及查询最近支付记录功能。
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentService paymentService;

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
     * 测试支付处理：验证支付记录正确创建，包括金额计算和状态设置。
     */
    @Test
    void testProcessPayment() {
        // 准备测试数据
        String orderId = "ORD-20260630-001";
        String method = "CASH";
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal received = new BigDecimal("150.00");
        String operator = "cashier01";

        mapperUtilsMock.when(() -> MapperUtils.insert(eq(paymentMapper), any(PaymentEntity.class)))
                .thenAnswer(invocation -> {
                    PaymentEntity entity = invocation.getArgument(1);
                    return entity;
                });

        // 执行测试
        PaymentEntity result = paymentService.processPayment(orderId, method, amount, received, operator);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(orderId, result.getOrderId());
        assertEquals(method, result.getMethod());
        assertEquals(amount, result.getAmount());
        assertEquals(received, result.getReceivedAmount());
        assertEquals(new BigDecimal("50.00"), result.getChangeAmount());
        assertEquals("PAID", result.getStatus());
        assertEquals(operator, result.getOperator());
        assertNotNull(result.getPaidAt());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.insert(eq(paymentMapper), any(PaymentEntity.class)));
    }

    @Test
    void processPaymentAllowsZeroAmount() {
        String orderId = "ORD-FREE-001";
        BigDecimal amount = BigDecimal.ZERO;
        String operator = "cashier01";

        mapperUtilsMock.when(() -> MapperUtils.insert(eq(paymentMapper), any(PaymentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        PaymentEntity result = paymentService.processPayment(
                orderId, "CASH", amount, null, operator, "idem-free"
        );

        assertNotNull(result);
        assertEquals(amount, result.getAmount());
        assertEquals(BigDecimal.ZERO, result.getReceivedAmount());
        assertEquals(BigDecimal.ZERO, result.getChangeAmount());
        assertEquals("PAID", result.getStatus());
    }

    @Test
    void processPaymentRejectsCashReceivedBelowAmount() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> paymentService.processPayment(
                        "ORD-CASH-SHORT", "CASH",
                        new BigDecimal("100.00"), new BigDecimal("99.99"),
                        "cashier01", "idem-cash-short"
                ));

        assertEquals("Received amount must cover the payment amount", error.getMessage());
        mapperUtilsMock.verify(
                () -> MapperUtils.insert(eq(paymentMapper), any(PaymentEntity.class)), never()
        );
    }

    /**
     * 测试按订单ID查询支付记录：验证能正确过滤并返回指定订单的支付记录。
     */
    @Test
    void testListByOrderId() {
        // 准备测试数据
        String orderId = "ORD-20260630-001";

        PaymentEntity payment = new PaymentEntity();
        payment.setId("pay001");
        payment.setOrderId(orderId);
        payment.setMethod("CASH");
        payment.setAmount(new BigDecimal("100.00"));

        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(paymentMapper), eq(PaymentEntity.class), any()))
                .thenReturn(List.of(payment));

        // 执行测试
        List<PaymentEntity> result = paymentService.listByOrderId(orderId);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(orderId, result.get(0).getOrderId());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.selectList(eq(paymentMapper), eq(PaymentEntity.class), any()));
    }

    /**
     * 测试查询最近支付记录：验证按时间倒序返回指定数量的支付记录。
     */
    @Test
    void testListRecent() {
        // 准备测试数据
        int limit = 5;

        PaymentEntity payment1 = new PaymentEntity();
        payment1.setId("pay001");
        payment1.setAmount(new BigDecimal("100.00"));

        PaymentEntity payment2 = new PaymentEntity();
        payment2.setId("pay002");
        payment2.setAmount(new BigDecimal("200.00"));

        mapperUtilsMock.when(() -> MapperUtils.selectList(eq(paymentMapper), eq(PaymentEntity.class), any()))
                .thenReturn(List.of(payment2, payment1));

        // 执行测试
        List<PaymentEntity> result = paymentService.listRecent(limit);

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.size());

        // 验证MapperUtils调用
        mapperUtilsMock.verify(() -> MapperUtils.selectList(eq(paymentMapper), eq(PaymentEntity.class), any()));
    }
}
