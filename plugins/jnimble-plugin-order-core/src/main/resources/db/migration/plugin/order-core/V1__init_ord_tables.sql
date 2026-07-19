CREATE TABLE IF NOT EXISTS ord_table (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(50)    NOT NULL COMMENT 'Table code/number',
    area        VARCHAR(50)    NULL COMMENT 'Area name (hall, room, etc.)',
    seat_count  INT            NOT NULL DEFAULT 4 COMMENT 'Number of seats',
    status      VARCHAR(20)    NOT NULL DEFAULT 'FREE' COMMENT 'FREE, OCCUPIED, RESERVED, CLEANING',
    qr_code_url VARCHAR(500)   NULL COMMENT 'QR code URL for scanning',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'Dining table';

CREATE TABLE IF NOT EXISTS ord_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32)    NOT NULL COMMENT 'Order number',
    table_id        BIGINT         NULL COMMENT 'Associated table ID',
    party_size      INT            NULL COMMENT 'Number of guests',
    status          VARCHAR(20)    NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, CONFIRMED, SETTLED, CANCELLED',
    total_amount    DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT 'Total order amount',
    discount_amount DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT 'Discount amount',
    final_amount    DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT 'Final payable amount',
    payment_method  VARCHAR(50)    NULL COMMENT 'Payment method: CASH, WECHAT, ALIPAY, CARD',
    remark          VARCHAR(500)   NULL COMMENT 'Order remarks',
    operator        VARCHAR(50)    NULL COMMENT 'Operator who created the order',
    settled_at      DATETIME       NULL COMMENT 'Settlement time',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_table_id (table_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) COMMENT 'Order';

CREATE TABLE IF NOT EXISTS ord_order_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT         NOT NULL COMMENT 'Order ID',
    menu_item_id BIGINT        NULL COMMENT 'Menu item ID',
    item_name   VARCHAR(200)   NOT NULL COMMENT 'Item name at time of order',
    quantity    INT            NOT NULL DEFAULT 1 COMMENT 'Quantity',
    unit_price  DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT 'Unit price',
    subtotal    DECIMAL(10,2)  NOT NULL DEFAULT 0.00 COMMENT 'Subtotal',
    remark      VARCHAR(500)   NULL COMMENT 'Item remark',
    status      VARCHAR(20)    NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL, CANCELLED',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id)
) COMMENT 'Order item';
