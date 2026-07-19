ALTER TABLE ord_order_item
    ADD COLUMN batch_seq INT NOT NULL DEFAULT 0 COMMENT 'Order batch sequence (1=initial, 2+=add-on rounds)' AFTER status;
