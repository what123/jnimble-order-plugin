package com.jnimble.plugin.printer.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("prn_print_template")
public class PrintTemplateEntity {

    @TableId
    private String id;
    private String code;
    private String name;
    private Integer paperWidthMm;
    private String status;
    private String draftDefinitionJson;
    private Integer draftRevision;
    private Integer publishedVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPaperWidthMm() {
        return paperWidthMm;
    }

    public void setPaperWidthMm(Integer paperWidthMm) {
        this.paperWidthMm = paperWidthMm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDraftDefinitionJson() {
        return draftDefinitionJson;
    }

    public void setDraftDefinitionJson(String draftDefinitionJson) {
        this.draftDefinitionJson = draftDefinitionJson;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
