package com.jnimble.plugin.printer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jnimble.plugin.printer.mapper.PrintTemplateMapper;
import com.jnimble.plugin.printer.mapper.PrintTemplateVersionMapper;
import com.jnimble.plugin.printer.model.dto.PrintTemplateDetail;
import com.jnimble.plugin.printer.model.dto.PrintTemplateSaveRequest;
import com.jnimble.plugin.printer.model.entity.PrintTemplateEntity;
import com.jnimble.plugin.printer.model.entity.PrintTemplateVersionEntity;
import com.jnimble.plugin.printer.template.PrintBlockDescriptor;
import com.jnimble.plugin.printer.template.PrintTemplateExtensionRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PrintTemplateServiceTest {

    private ObjectMapper objectMapper;
    private PrintTemplateMapper templateMapper;
    private PrintTemplateVersionMapper versionMapper;
    private PrintTemplateJsonService jsonService;
    private PrintTemplateService templateService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        templateMapper = mock(PrintTemplateMapper.class);
        versionMapper = mock(PrintTemplateVersionMapper.class);
        PrintTemplateExtensionRegistry registry = new PrintTemplateExtensionRegistry(objectMapper);
        jsonService = new PrintTemplateJsonService(objectMapper, registry);
        templateService = new PrintTemplateService(templateMapper, versionMapper, jsonService);
        when(templateMapper.exists(any())).thenReturn(false);
        when(templateMapper.insert(any(PrintTemplateEntity.class))).thenReturn(1);
        when(templateMapper.updateById(any(PrintTemplateEntity.class))).thenReturn(1);
        when(versionMapper.insert(any(PrintTemplateVersionEntity.class))).thenReturn(1);
    }

    @Test
    void createTemplateShouldPersistFormattedDefinitionJson() {
        JsonNode definition = jsonService.defaultDefinition(58);

        PrintTemplateDetail created = templateService.createTemplate(
                new PrintTemplateSaveRequest("receipt-main", "前台小票", 58, null, definition)
        );

        ArgumentCaptor<PrintTemplateEntity> captor = ArgumentCaptor.forClass(PrintTemplateEntity.class);
        verify(templateMapper).insert(captor.capture());
        assertEquals("receipt-main", created.code());
        assertEquals(1, created.draftRevision());
        assertTrue(captor.getValue().getDraftDefinitionJson().contains(System.lineSeparator()));
        assertTrue(captor.getValue().getDraftDefinitionJson().contains("  \"paper\""));
    }

    @Test
    void saveDraftShouldRejectStaleRevision() {
        PrintTemplateEntity entity = entityWithDefinition(jsonService.defaultDefinition(58));
        entity.setDraftRevision(2);
        when(templateMapper.selectById("template-1")).thenReturn(entity);

        assertThrows(
                PrintTemplateConflictException.class,
                () -> templateService.saveDraft(
                        "template-1",
                        new PrintTemplateSaveRequest("receipt-main", "前台小票", 58, 1, jsonService.defaultDefinition(58))
                )
        );
    }

    @Test
    void publishShouldCreateImmutableVersionSnapshot() {
        PrintTemplateExtensionRegistry registry = new PrintTemplateExtensionRegistry(objectMapper);
        PrintBlockDescriptor title = registry.descriptors().stream()
                .filter(item -> "core.title".equals(item.type()))
                .findFirst()
                .orElseThrow();
        ObjectNode definition = (ObjectNode) jsonService.defaultDefinition(58);
        ArrayNode blocks = (ArrayNode) definition.path("rows").get(0).path("blocks");
        ObjectNode block = blocks.addObject();
        block.put("id", "title");
        block.put("type", title.type());
        block.put("provider", title.provider());
        block.put("providerVersion", title.version());
        block.put("widthBasisPoints", 10000);
        block.put("optional", false);
        block.set("config", objectMapper.valueToTree(title.defaultConfig()));

        PrintTemplateEntity entity = entityWithDefinition(definition);
        when(templateMapper.selectById("template-1")).thenReturn(entity);

        PrintTemplateDetail published = templateService.publish("template-1", "admin");

        ArgumentCaptor<PrintTemplateVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(PrintTemplateVersionEntity.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertEquals(1, versionCaptor.getValue().getVersion());
        assertEquals("admin", versionCaptor.getValue().getPublishedBy());
        assertTrue(versionCaptor.getValue().getDefinitionJson().contains(System.lineSeparator()));
        assertEquals("PUBLISHED", published.status());
        assertEquals(1, published.publishedVersion());
    }

    private PrintTemplateEntity entityWithDefinition(JsonNode definition) {
        PrintTemplateEntity entity = new PrintTemplateEntity();
        entity.setId("template-1");
        entity.setCode("receipt-main");
        entity.setName("前台小票");
        entity.setPaperWidthMm(58);
        entity.setStatus("DRAFT");
        entity.setDraftRevision(1);
        entity.setDraftDefinitionJson(jsonService.format(definition));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
