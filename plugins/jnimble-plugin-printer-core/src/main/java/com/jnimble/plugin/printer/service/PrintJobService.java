package com.jnimble.plugin.printer.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.printer.mapper.PrintJobMapper;
import com.jnimble.plugin.printer.model.dto.PrintTemplatePreviewResponse;
import com.jnimble.plugin.printer.model.dto.PublishedPrintTemplate;
import com.jnimble.plugin.printer.model.entity.PrintJobEntity;
import com.jnimble.plugin.printer.model.entity.PrintNodeEntity;
import com.jnimble.plugin.printer.model.entity.PrinterEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PrintJobService {

    private final PrintJobMapper printJobMapper;

    public PrintJobService(PrintJobMapper printJobMapper) {
        this.printJobMapper = printJobMapper;
    }

    public PrintJobEntity createJob(String orderId, String printerId, String nodeName,
                                    String type, String content, String driverId) {
        PrintJobEntity entity = new PrintJobEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setOrderId(orderId);
        entity.setPrinterId(printerId);
        entity.setNodeName(nodeName);
        entity.setType(type);
        entity.setContent(content);
        entity.setDriverId(driverId);
        entity.setStatus("PENDING");
        entity.setRetryCount(0);
        entity.setMaxRetries(3);
        entity.setCreatedAt(LocalDateTime.now());
        return MapperUtils.insert(printJobMapper, entity);
    }

    public PrintJobEntity createDocumentJob(
            String orderId,
            PrintNodeEntity node,
            PrinterEntity printer,
            PublishedPrintTemplate template,
            PrintTemplatePreviewResponse rendered
    ) {
        PrintJobEntity entity = new PrintJobEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setOrderId(orderId);
        entity.setPrinterId(printer.getId());
        entity.setNodeName(node.getNodeName());
        entity.setTemplateId(template.templateId());
        entity.setTemplateVersion(template.version());
        entity.setDocumentSchemaVersion(rendered.document().path("schemaVersion").asInt(1));
        entity.setDocumentJson(rendered.documentJson());
        entity.setDocumentDigest(rendered.document().path("documentDigest").asText());
        entity.setType("KITCHEN");
        entity.setContent(rendered.documentJson());
        entity.setContentType("application/vnd.jnimble.print-document+json; charset=UTF-8");
        entity.setContentEncoding("PLAIN");
        entity.setDataDigest(rendered.document().path("dataDigest").asText());
        entity.setDriverId(printer.getDriverId());
        entity.setStatus("PENDING");
        entity.setRetryCount(0);
        entity.setMaxRetries(3);
        entity.setCreatedAt(LocalDateTime.now());
        return MapperUtils.insert(printJobMapper, entity);
    }

    public List<PrintJobEntity> listJobs(String status, LocalDateTime dateFrom, LocalDateTime dateTo) {
        return MapperUtils.selectList(printJobMapper, PrintJobEntity.class, wrapper -> {
            if (status != null && !status.isBlank()) {
                wrapper.eq("status", status);
            }
            if (dateFrom != null) {
                wrapper.ge("created_at", dateFrom);
            }
            if (dateTo != null) {
                wrapper.le("created_at", dateTo);
            }
            wrapper.orderByDesc("created_at");
        });
    }

    public PrintJobEntity retryJob(String jobId) {
        PrintJobEntity entity = MapperUtils.getById(printJobMapper, jobId, "Print job not found: " + jobId);
        entity.setRetryCount(entity.getRetryCount() + 1);
        entity.setStatus("PENDING");
        entity.setErrorMessage(null);
        MapperUtils.updateById(printJobMapper, entity);
        return entity;
    }

    /**
     * 拉取待调度任务,按创建时间升序,只取前 {@code limit} 条。
     */
    public List<PrintJobEntity> listPendingJobs(int limit) {
        return MapperUtils.selectList(printJobMapper, PrintJobEntity.class, wrapper -> {
            wrapper.eq("status", "PENDING")
                    .orderByAsc("created_at")
                    .last("LIMIT " + Math.max(1, limit));
        });
    }

    /**
     * 标记任务执行成功。
     */
    public void markSuccess(String jobId, String externalOrderId) {
        PrintJobEntity entity = MapperUtils.getById(printJobMapper, jobId, "Print job not found: " + jobId);
        entity.setStatus("SUCCESS");
        entity.setPrintedAt(Instant.now());
        if (externalOrderId != null && !externalOrderId.isBlank()) {
            entity.setExternalOrderId(externalOrderId);
        }
        entity.setErrorMessage(null);
        MapperUtils.updateById(printJobMapper, entity);
    }

    /**
     * 标记任务执行失败,根据 retryCount/maxRetries 决定是否仍可重试。
     */
    public void markFailed(String jobId, String errorMessage) {
        PrintJobEntity entity = MapperUtils.getById(printJobMapper, jobId, "Print job not found: " + jobId);
        int retryCount = (entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1;
        int maxRetries = entity.getMaxRetries() == null ? 3 : entity.getMaxRetries();
        entity.setRetryCount(retryCount);
        entity.setErrorMessage(truncate(errorMessage, 3800));
        if (retryCount >= maxRetries) {
            entity.setStatus("FAILED");
        } else {
            entity.setStatus("PENDING");
        }
        MapperUtils.updateById(printJobMapper, entity);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
