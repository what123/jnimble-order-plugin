DROP TABLE IF EXISTS menu_item_spec;

CREATE TABLE IF NOT EXISTS menu_item_spec_group (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id     BIGINT       NOT NULL COMMENT 'Menu item ID',
    name        VARCHAR(100) NOT NULL COMMENT 'Group name (e.g. size, spiciness)',
    required    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT 'Whether selection is required',
    multi       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'Allow multiple selections',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT 'Display sort order',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_item_id (item_id)
) COMMENT 'Menu item spec groups (dimensions)';

CREATE TABLE IF NOT EXISTS menu_item_spec_option (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id     BIGINT        NOT NULL COMMENT 'Spec group ID',
    name         VARCHAR(100)  NOT NULL COMMENT 'Option name',
    price_adjust DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Price adjustment',
    sort_order   INT           NOT NULL DEFAULT 0 COMMENT 'Display sort order',
    status       VARCHAR(20)   NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED, DISABLED',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_group_id (group_id)
) COMMENT 'Menu item spec options within a group';
