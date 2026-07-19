CREATE TABLE IF NOT EXISTS menu_item_spec (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id     BIGINT         NOT NULL COMMENT 'Menu item ID',
    name        VARCHAR(100)   NOT NULL COMMENT 'Spec name (e.g. small, large)',
    price       DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT 'Spec price',
    unit        VARCHAR(50)    NULL COMMENT 'Spec unit',
    sort_order  INT            NOT NULL DEFAULT 0 COMMENT 'Display sort order',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_item_id (item_id)
) COMMENT 'Menu item specifications for multi-spec support';
