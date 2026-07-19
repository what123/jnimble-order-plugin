ALTER TABLE pay_payment
    ADD COLUMN request_snapshot LONGTEXT NULL COMMENT 'Sanitized payment request snapshot' AFTER idempotency_key,
    ADD COLUMN response_snapshot LONGTEXT NULL COMMENT 'Sanitized payment response snapshot' AFTER request_snapshot;

CREATE TABLE IF NOT EXISTS pay_payment_event (
    id                VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT 'Primary key',
    payment_id        VARCHAR(32)  NULL COMMENT 'Payment ID',
    order_id          VARCHAR(32)  NULL COMMENT 'Order ID',
    event_type        VARCHAR(32)  NOT NULL COMMENT 'Event type',
    status            VARCHAR(20)  NOT NULL COMMENT 'Event status',
    request_snapshot  LONGTEXT     NULL COMMENT 'Sanitized request snapshot',
    response_snapshot LONGTEXT     NULL COMMENT 'Sanitized response snapshot',
    error_message     VARCHAR(500) NULL COMMENT 'Sanitized error message',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    KEY idx_pay_event_payment (payment_id),
    KEY idx_pay_event_order (order_id),
    KEY idx_pay_event_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Payment diagnostic events';
