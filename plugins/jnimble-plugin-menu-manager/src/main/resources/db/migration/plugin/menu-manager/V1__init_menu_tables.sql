CREATE TABLE IF NOT EXISTS menu_category (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)   NOT NULL COMMENT 'Category name',
    sort_order  INT            NOT NULL DEFAULT 0 COMMENT 'Display sort order',
    status      VARCHAR(20)    NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED, DISABLED',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'Menu category';

CREATE TABLE IF NOT EXISTS menu_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT         NOT NULL COMMENT 'Category ID',
    name        VARCHAR(200)   NOT NULL COMMENT 'Item name',
    price       DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT 'Selling price',
    unit        VARCHAR(50)    NULL COMMENT 'Selling unit',
    image_path  VARCHAR(500)   NULL COMMENT 'Item image path',
    description TEXT           NULL COMMENT 'Item description',
    status      VARCHAR(20)    NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED, DISABLED',
    sort_order  INT            NOT NULL DEFAULT 0 COMMENT 'Display sort order',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category_id (category_id)
) COMMENT 'Menu item';
