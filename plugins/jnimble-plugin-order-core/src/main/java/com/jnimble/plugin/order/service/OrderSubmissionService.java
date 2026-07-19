package com.jnimble.plugin.order.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.mapper.OrderSubmissionMapper;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import com.jnimble.plugin.order.model.entity.OrderSubmissionEntity;
import com.jnimble.plugin.order.submission.OrderConfirmationMode;
import com.jnimble.plugin.order.submission.OrderSubmissionCommand;
import com.jnimble.plugin.order.submission.OrderSubmissionStatus;
import com.jnimble.plugin.order.submission.OrderSubmissionType;
import com.jnimble.plugin.order.submission.OrderSubmissionView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSubmissionService {

    private final OrderSubmissionMapper submissionMapper;
    private final OrderService orderService;

    public OrderSubmissionService(OrderSubmissionMapper submissionMapper,
                                  OrderService orderService) {
        this.submissionMapper = submissionMapper;
        this.orderService = orderService;
    }

    @Transactional
    public OrderSubmissionView submitInitial(Long orderId, OrderSubmissionCommand command) {
        ValidatedCommand validated = validate(command);
        OrderSubmissionEntity existing = existingSubmission(orderId, validated.idempotencyKey());
        if (existing != null) {
            return toView(existing);
        }
        orderService.prepareInitialBatch(orderId);
        OrderSubmissionEntity submission = createSubmission(
                orderId, 1, OrderSubmissionType.INITIAL, validated
        );
        if (validated.confirmationMode() == OrderConfirmationMode.AUTO) {
            confirmPending(submission, validated.submittedBy());
        }
        return toView(submission);
    }

    @Transactional
    public OrderSubmissionView submitItems(Long orderId, List<OrderItemEntity> items,
                                           OrderSubmissionCommand command) {
        ValidatedCommand validated = validate(command);
        OrderSubmissionEntity existing = existingSubmission(orderId, validated.idempotencyKey());
        if (existing != null) {
            return toView(existing);
        }
        OrderEntity order = orderService.getOrder(orderId);
        if (!"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException("Add-on submission requires a confirmed order");
        }
        List<OrderItemEntity> created = orderService.stageBatchItems(orderId, items);
        int batchSeq = created.getFirst().getBatchSeq();
        OrderSubmissionEntity submission = createSubmission(
                orderId, batchSeq, OrderSubmissionType.ADD_ON, validated
        );
        if (validated.confirmationMode() == OrderConfirmationMode.AUTO) {
            confirmPending(submission, validated.submittedBy());
        }
        return toView(submission);
    }

    @Transactional(readOnly = true)
    public List<OrderSubmissionView> listPending() {
        return MapperUtils.selectList(
                        submissionMapper,
                        OrderSubmissionEntity.class,
                        wrapper -> wrapper.eq("status", OrderSubmissionStatus.PENDING.name())
                                .orderByAsc("submitted_at")
                                .orderByAsc("id")
                ).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasPendingForOrder(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return !MapperUtils.selectList(
                submissionMapper,
                OrderSubmissionEntity.class,
                wrapper -> wrapper.eq("order_id", orderId)
                        .eq("status", OrderSubmissionStatus.PENDING.name())
        ).isEmpty();
    }

    @Transactional
    public OrderSubmissionView confirm(Long submissionId, String operator) {
        OrderSubmissionEntity submission = requireForUpdate(submissionId);
        if (OrderSubmissionStatus.CONFIRMED.name().equals(submission.getStatus())) {
            return toView(submission);
        }
        requirePending(submission);
        confirmPending(submission, normalizeOperator(operator));
        return toView(submission);
    }

    @Transactional
    public OrderSubmissionView returnSubmission(Long submissionId, String operator, String reason) {
        OrderSubmissionEntity submission = requireForUpdate(submissionId);
        if (OrderSubmissionStatus.RETURNED.name().equals(submission.getStatus())) {
            return toView(submission);
        }
        requirePending(submission);
        String normalizedReason = normalizeRequired(reason, "Return reason", 500);
        if (OrderSubmissionType.ADD_ON.name().equals(submission.getBatchType())) {
            orderService.cancelAddOnBatch(submission.getOrderId(), submission.getBatchSeq());
        }
        LocalDateTime now = LocalDateTime.now();
        OrderSubmissionEntity update = new OrderSubmissionEntity();
        update.setId(submissionId);
        update.setStatus(OrderSubmissionStatus.RETURNED.name());
        update.setReturnedBy(normalizeOperator(operator));
        update.setReturnedAt(now);
        update.setReturnReason(normalizedReason);
        update.setUpdatedAt(now);
        MapperUtils.updateById(submissionMapper, update);
        submission.setStatus(update.getStatus());
        submission.setReturnedBy(update.getReturnedBy());
        submission.setReturnedAt(now);
        submission.setReturnReason(normalizedReason);
        submission.setUpdatedAt(now);
        return toView(submission);
    }

    @Transactional(readOnly = true)
    public OrderSubmissionView get(Long submissionId) {
        return toView(MapperUtils.getById(
                submissionMapper, submissionId, "Order submission not found: " + submissionId
        ));
    }

    private OrderSubmissionEntity createSubmission(Long orderId, int batchSeq,
                                                     OrderSubmissionType batchType,
                                                     ValidatedCommand command) {
        LocalDateTime now = LocalDateTime.now();
        OrderSubmissionEntity entity = new OrderSubmissionEntity();
        entity.setOrderId(orderId);
        entity.setBatchSeq(batchSeq);
        entity.setBatchType(batchType.name());
        entity.setSourceType(command.sourceType());
        entity.setConfirmationMode(command.confirmationMode().name());
        entity.setStatus(OrderSubmissionStatus.PENDING.name());
        entity.setIdempotencyKey(command.idempotencyKey());
        entity.setSubmittedBy(command.submittedBy());
        entity.setSubmittedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        MapperUtils.insert(submissionMapper, entity);
        return entity;
    }

    private void confirmPending(OrderSubmissionEntity submission, String operator) {
        orderService.confirmBatch(submission.getOrderId(), submission.getBatchSeq());
        LocalDateTime now = LocalDateTime.now();
        OrderSubmissionEntity update = new OrderSubmissionEntity();
        update.setId(submission.getId());
        update.setStatus(OrderSubmissionStatus.CONFIRMED.name());
        update.setConfirmedBy(normalizeOperator(operator));
        update.setConfirmedAt(now);
        update.setUpdatedAt(now);
        MapperUtils.updateById(submissionMapper, update);
        submission.setStatus(update.getStatus());
        submission.setConfirmedBy(update.getConfirmedBy());
        submission.setConfirmedAt(now);
        submission.setUpdatedAt(now);
    }

    private OrderSubmissionEntity existingSubmission(Long orderId, String idempotencyKey) {
        OrderSubmissionEntity existing = submissionMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null && !orderId.equals(existing.getOrderId())) {
            throw new IllegalStateException("Idempotency key belongs to another order");
        }
        return existing;
    }

    private OrderSubmissionEntity requireForUpdate(Long submissionId) {
        OrderSubmissionEntity submission = submissionMapper.selectByIdForUpdate(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("Order submission not found: " + submissionId);
        }
        return submission;
    }

    private void requirePending(OrderSubmissionEntity submission) {
        if (!OrderSubmissionStatus.PENDING.name().equals(submission.getStatus())) {
            throw new IllegalStateException("Order submission is no longer pending");
        }
    }

    private OrderSubmissionView toView(OrderSubmissionEntity submission) {
        return new OrderSubmissionView(
                submission,
                orderService.getOrder(submission.getOrderId()),
                orderService.getOrderItemsByBatch(submission.getOrderId(), submission.getBatchSeq())
        );
    }

    private ValidatedCommand validate(OrderSubmissionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Submission command is required");
        }
        String sourceType = normalizeRequired(command.sourceType(), "Source type", 50)
                .toUpperCase(Locale.ROOT);
        if (command.confirmationMode() == null) {
            throw new IllegalArgumentException("Confirmation mode is required");
        }
        String idempotencyKey = normalizeRequired(command.idempotencyKey(), "Idempotency key", 100);
        return new ValidatedCommand(
                sourceType,
                command.confirmationMode(),
                trimToNull(command.submittedBy()),
                idempotencyKey
        );
    }

    private String normalizeRequired(String value, String label, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return normalized;
    }

    private String normalizeOperator(String operator) {
        String normalized = trimToNull(operator);
        return normalized == null ? "system" : normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedCommand(
            String sourceType,
            OrderConfirmationMode confirmationMode,
            String submittedBy,
            String idempotencyKey
    ) {
    }
}
