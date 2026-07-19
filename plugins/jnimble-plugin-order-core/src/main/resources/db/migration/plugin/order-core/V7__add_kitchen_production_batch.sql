ALTER TABLE ord_kitchen_queue_item
    ADD COLUMN order_batch_seq INT NOT NULL DEFAULT 0 COMMENT 'Order add-on batch sequence snapshot' AFTER quantity,
    ADD COLUMN production_batch_no VARCHAR(64) NULL COMMENT 'Kitchen production batch number' AFTER order_batch_seq,
    ADD INDEX idx_kitchen_queue_production_batch (production_batch_no, status);
