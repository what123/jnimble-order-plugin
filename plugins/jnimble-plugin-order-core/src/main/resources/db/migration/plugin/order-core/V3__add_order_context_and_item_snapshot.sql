ALTER TABLE ord_order
    ADD COLUMN session_id BIGINT NULL COMMENT 'Ordering-mode session reference' AFTER table_id,
    ADD COLUMN business_date DATE NULL COMMENT 'Business date' AFTER party_size,
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'LEGACY' COMMENT 'POS, QR, WAITER, IMPORT, LEGACY' AFTER business_date,
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version' AFTER source,
    ADD INDEX idx_ord_order_session_id (session_id),
    ADD INDEX idx_ord_order_business_date (business_date);

UPDATE ord_order
SET business_date = DATE(created_at)
WHERE business_date IS NULL;

ALTER TABLE ord_order
    MODIFY COLUMN business_date DATE NOT NULL COMMENT 'Business date';

ALTER TABLE ord_order_item
    ADD COLUMN specification VARCHAR(500) NULL COMMENT 'Selected specification snapshot' AFTER item_name;
