package com.jnimble.plugin.printer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.printer.mapper.PrintTemplateMapper;
import com.jnimble.plugin.printer.mapper.PrintTemplateVersionMapper;
import com.jnimble.plugin.printer.model.dto.PrintTemplateDetail;
import com.jnimble.plugin.printer.model.dto.PublishedPrintTemplate;
import com.jnimble.plugin.printer.model.dto.PrintTemplateSaveRequest;
import com.jnimble.plugin.printer.model.dto.PrintTemplateSummary;
import com.jnimble.plugin.printer.model.entity.PrintTemplateEntity;
import com.jnimble.plugin.printer.model.entity.PrintTemplateVersionEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrintTemplateService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{1,63}");

    private final PrintTemplateMapper templateMapper;
    private final PrintTemplateVersionMapper versionMapper;
    private final PrintTemplateJsonService jsonService;

    public PrintTemplateService(
            PrintTemplateMapper templateMapper,
            PrintTemplateVersionMapper versionMapper,
            PrintTemplateJsonService jsonService
    ) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.jsonService = jsonService;
    }

    @Transactional(readOnly = true)
    public List<PrintTemplateSummary> listTemplates() {
        return MapperUtils.selectList(
                        templateMapper,
                        PrintTemplateEntity.class,
                        wrapper -> wrapper.orderByDesc("updated_at")
                ).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PrintTemplateDetail getTemplate(String id) {
        return toDetail(requireTemplate(id));
    }

    @Transactional(readOnly = true)
    public boolean isPublishedTemplate(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        PrintTemplateEntity entity = MapperUtils.getById(templateMapper, id, null);
        return entity != null
                && "PUBLISHED".equals(entity.getStatus())
                && entity.getPublishedVersion() != null;
    }

    @Transactional(readOnly = true)
    public PublishedPrintTemplate getPublishedTemplate(String id) {
        PrintTemplateEntity entity = requireTemplate(id);
        if (!"PUBLISHED".equals(entity.getStatus()) || entity.getPublishedVersion() == null) {
            throw new IllegalStateException("打印节点绑定的模板尚未发布");
        }
        PrintTemplateVersionEntity version = MapperUtils.selectOne(
                versionMapper,
                PrintTemplateVersionEntity.class,
                wrapper -> wrapper.eq("template_id", entity.getId())
                        .eq("version", entity.getPublishedVersion())
        );
        if (version == null) {
            throw new IllegalStateException("找不到打印模板的已发布版本");
        }
        return new PublishedPrintTemplate(
                entity.getId(),
                version.getVersion(),
                entity.getPaperWidthMm(),
                jsonService.parse(version.getDefinitionJson())
        );
    }

    @Transactional
    public PrintTemplateDetail createTemplate(PrintTemplateSaveRequest request) {
        String name = requireName(request == null ? null : request.name());
        String code = requireCode(request == null ? null : request.code());
        int paperWidth = requirePaperWidth(request == null ? null : request.paperWidthMm());
        ensureUniqueCode(code, null);

        JsonNode definition = jsonService.prepareDefinition(request.definition(), paperWidth);
        jsonService.validateOrThrow(definition, paperWidth, false);

        LocalDateTime now = LocalDateTime.now();
        PrintTemplateEntity entity = new PrintTemplateEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCode(code);
        entity.setName(name);
        entity.setPaperWidthMm(paperWidth);
        entity.setStatus("DRAFT");
        entity.setDraftDefinitionJson(jsonService.format(definition));
        entity.setDraftRevision(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        MapperUtils.insert(templateMapper, entity);
        return toDetail(entity);
    }

    @Transactional
    public PrintTemplateDetail saveDraft(String id, PrintTemplateSaveRequest request) {
        PrintTemplateEntity entity = requireTemplate(id);
        if (request == null || request.draftRevision() == null
                || !request.draftRevision().equals(entity.getDraftRevision())) {
            throw new PrintTemplateConflictException("模板草稿已被其他操作更新，请刷新后重试");
        }

        String name = requireName(request.name());
        String code = requireCode(request.code());
        int paperWidth = requirePaperWidth(request.paperWidthMm());
        ensureUniqueCode(code, id);

        JsonNode definition = jsonService.prepareDefinition(request.definition(), paperWidth);
        jsonService.validateOrThrow(definition, paperWidth, false);

        entity.setCode(code);
        entity.setName(name);
        entity.setPaperWidthMm(paperWidth);
        entity.setDraftDefinitionJson(jsonService.format(definition));
        entity.setDraftRevision(entity.getDraftRevision() + 1);
        entity.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(templateMapper, entity);
        return toDetail(entity);
    }

    @Transactional
    public PrintTemplateDetail publish(String id, String publisher) {
        PrintTemplateEntity entity = requireTemplate(id);
        JsonNode definition = jsonService.parse(entity.getDraftDefinitionJson());
        jsonService.validateOrThrow(definition, entity.getPaperWidthMm(), true);

        int version = entity.getPublishedVersion() == null ? 1 : entity.getPublishedVersion() + 1;
        PrintTemplateVersionEntity versionEntity = new PrintTemplateVersionEntity();
        versionEntity.setId(UUID.randomUUID().toString());
        versionEntity.setTemplateId(entity.getId());
        versionEntity.setVersion(version);
        versionEntity.setSchemaVersion(PrintTemplateJsonService.SCHEMA_VERSION);
        versionEntity.setDefinitionJson(jsonService.format(definition));
        versionEntity.setProviderSnapshotJson(jsonService.providerSnapshot(definition));
        versionEntity.setPublishedAt(LocalDateTime.now());
        versionEntity.setPublishedBy(publisher);
        MapperUtils.insert(versionMapper, versionEntity);

        entity.setStatus("PUBLISHED");
        entity.setPublishedVersion(version);
        entity.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(templateMapper, entity);
        return toDetail(entity);
    }

    private PrintTemplateEntity requireTemplate(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Template ID is required");
        }
        return MapperUtils.getById(templateMapper, id, "Print template not found");
    }

    private void ensureUniqueCode(String code, String currentId) {
        boolean duplicate = MapperUtils.existsByCondition(
                templateMapper,
                PrintTemplateEntity.class,
                wrapper -> {
                    wrapper.eq("code", code);
                    if (currentId != null) {
                        wrapper.ne("id", currentId);
                    }
                }
        );
        if (duplicate) {
            throw new IllegalArgumentException("模板编码已存在");
        }
    }

    private String requireCode(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("模板编码需为 2-64 位小写字母、数字、点、横线或下划线");
        }
        return normalized;
    }

    private String requireName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new IllegalArgumentException("模板名称不能为空且不能超过 128 个字符");
        }
        return normalized;
    }

    private int requirePaperWidth(Integer paperWidth) {
        if (paperWidth == null || (paperWidth != 58 && paperWidth != 80)) {
            throw new IllegalArgumentException("纸宽只能是 58mm 或 80mm");
        }
        return paperWidth;
    }

    private PrintTemplateSummary toSummary(PrintTemplateEntity entity) {
        return new PrintTemplateSummary(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getPaperWidthMm(),
                entity.getStatus(),
                entity.getDraftRevision(),
                entity.getPublishedVersion(),
                entity.getUpdatedAt()
        );
    }

    private PrintTemplateDetail toDetail(PrintTemplateEntity entity) {
        return new PrintTemplateDetail(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getPaperWidthMm(),
                entity.getStatus(),
                entity.getDraftRevision(),
                entity.getPublishedVersion(),
                jsonService.parse(entity.getDraftDefinitionJson()),
                entity.getDraftDefinitionJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
