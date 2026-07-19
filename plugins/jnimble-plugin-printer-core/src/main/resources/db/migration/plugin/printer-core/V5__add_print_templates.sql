CREATE TABLE IF NOT EXISTS prn_print_template (
    id                    VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT 'Template ID',
    code                  VARCHAR(64)  NOT NULL COMMENT 'Stable template code',
    name                  VARCHAR(128) NOT NULL COMMENT 'Template display name',
    paper_width_mm        INT          NOT NULL COMMENT 'Paper width: 58 or 80',
    status                VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, PUBLISHED, DISABLED',
    draft_definition_json LONGTEXT     NOT NULL COMMENT 'Formatted TemplateDefinition JSON',
    draft_revision        INT          NOT NULL DEFAULT 1 COMMENT 'Optimistic draft revision',
    published_version     INT          NULL COMMENT 'Current immutable published version',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_print_template_code (code),
    INDEX idx_print_template_status (status)
) COMMENT 'Visual print template';

CREATE TABLE IF NOT EXISTS prn_print_template_version (
    id                     VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT 'Version ID',
    template_id            VARCHAR(64)  NOT NULL COMMENT 'Template ID',
    version                INT          NOT NULL COMMENT 'Version number',
    schema_version         INT          NOT NULL COMMENT 'Template JSON schema version',
    definition_json        LONGTEXT     NOT NULL COMMENT 'Formatted immutable TemplateDefinition JSON',
    provider_snapshot_json LONGTEXT     NOT NULL COMMENT 'Formatted provider version snapshot JSON',
    published_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_by           VARCHAR(128) NULL COMMENT 'Publisher username',
    UNIQUE KEY uk_print_template_version (template_id, version),
    INDEX idx_print_template_version_template (template_id)
) COMMENT 'Immutable visual print template version';

ALTER TABLE prn_print_node
    ADD COLUMN template_id VARCHAR(64) NULL COMMENT 'Visual print template ID' AFTER template_view;

ALTER TABLE prn_print_job
    ADD COLUMN template_id VARCHAR(64) NULL COMMENT 'Visual print template ID' AFTER node_name,
    ADD COLUMN template_version INT NULL COMMENT 'Visual print template version' AFTER template_id,
    ADD COLUMN document_schema_version INT NULL COMMENT 'PrintDocument schema version' AFTER template_version,
    ADD COLUMN document_json LONGTEXT NULL COMMENT 'Formatted driver-neutral PrintDocument JSON' AFTER document_schema_version,
    ADD COLUMN document_digest VARCHAR(64) NULL COMMENT 'SHA-256 of canonical PrintDocument JSON' AFTER document_json,
    ADD COLUMN content_type VARCHAR(128) NULL COMMENT 'Encoded payload media type' AFTER content,
    ADD COLUMN content_encoding VARCHAR(16) NULL COMMENT 'PLAIN or BASE64' AFTER content_type,
    ADD COLUMN data_digest VARCHAR(64) NULL COMMENT 'SHA-256 of canonical input data JSON' AFTER content_encoding;
