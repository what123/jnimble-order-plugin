CREATE TABLE IF NOT EXISTS sc_store (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200)   NOT NULL COMMENT 'Store name',
    lat             DECIMAL(10,7)  NULL COMMENT 'Latitude',
    lng             DECIMAL(10,7)  NULL COMMENT 'Longitude',
    address         VARCHAR(500)   NULL COMMENT 'Store address',
    phone           VARCHAR(50)    NULL COMMENT 'Contact phone',
    default_currency_code VARCHAR(10) NULL DEFAULT 'CNY' COMMENT 'Default currency code',
    currency_symbol VARCHAR(10)    NULL DEFAULT '¥' COMMENT 'Currency symbol',
    status          VARCHAR(20)    NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED, DISABLED',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'Store information';

CREATE TABLE IF NOT EXISTS sc_table (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id    BIGINT         NOT NULL COMMENT 'Store ID',
    table_name  VARCHAR(100)   NOT NULL COMMENT 'Table display name',
    code        VARCHAR(50)    NOT NULL COMMENT 'Table code',
    area        VARCHAR(50)    NULL COMMENT 'Area name',
    seat_count  INT            NOT NULL DEFAULT 4 COMMENT 'Seat count',
    uuid        VARCHAR(32)    NOT NULL COMMENT 'Table UUID for QR scanning',
    status      VARCHAR(20)    NOT NULL DEFAULT 'FREE' COMMENT 'FREE, OCCUPIED',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sc_table_uuid (uuid),
    INDEX idx_sc_table_store (store_id)
) COMMENT 'Consumer-facing dining table';

CREATE TABLE IF NOT EXISTS sc_consumer (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid        VARCHAR(32)    NOT NULL COMMENT 'Consumer UUID',
    nick_name   VARCHAR(100)   NULL COMMENT 'Nickname',
    photo_url   VARCHAR(500)   NULL COMMENT 'Avatar URL',
    phone       VARCHAR(50)    NULL COMMENT 'Phone number',
    access_token VARCHAR(64)   NULL COMMENT 'Access token for authentication',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sc_consumer_uuid (uuid),
    INDEX idx_sc_consumer_token (access_token)
) COMMENT 'Consumer information';

CREATE TABLE IF NOT EXISTS sc_cart (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_id     BIGINT       NULL COMMENT 'Consumer ID',
    store_id        BIGINT       NOT NULL COMMENT 'Store ID',
    table_id        BIGINT       NULL COMMENT 'Table ID',
    order_id        BIGINT       NULL COMMENT 'Associated order ID once order is created',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, ORDERED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sc_cart_consumer (consumer_id),
    INDEX idx_sc_cart_store (store_id),
    INDEX idx_sc_cart_table (table_id)
) COMMENT 'Shopping cart';

CREATE TABLE IF NOT EXISTS sc_cart_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id          BIGINT       NOT NULL COMMENT 'Cart ID',
    menu_item_id     BIGINT       NOT NULL COMMENT 'Menu item ID',
    item_name        VARCHAR(200) NOT NULL COMMENT 'Item name snapshot',
    unit_price       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Unit price',
    quantity         INT          NOT NULL DEFAULT 1 COMMENT 'Quantity',
    total_price      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Total price for this item',
    tag_ids          VARCHAR(500) NULL COMMENT 'Comma-separated spec option IDs',
    tag_names        VARCHAR(500) NULL COMMENT 'Comma-separated spec option names',
    remark           VARCHAR(500) NULL COMMENT 'Item remark',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sc_cart_item_cart (cart_id)
) COMMENT 'Shopping cart item';

-- Seed a default store and table for testing
INSERT INTO sc_store (name, address, default_currency_code, currency_symbol, status)
SELECT '示范餐厅', '示范地址', 'CNY', '¥', 'ENABLED'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_store);

INSERT INTO sc_table (store_id, table_name, code, seat_count, uuid, status)
SELECT s.id, 'A1', 'A1', 4, '24cff671b57c3419f4c70ba3bf74824f', 'FREE'
FROM sc_store s
WHERE NOT EXISTS (SELECT 1 FROM sc_table WHERE uuid = '24cff671b57c3419f4c70ba3bf74824f');

-- Seed a default consumer for testing
INSERT INTO sc_consumer (uuid, nick_name, access_token)
SELECT 'demo-consumer-uuid', '测试用户', 'demo-access-token-888888'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_consumer);
