ALTER TABLE pay_payment
    ADD COLUMN provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL' COMMENT 'Payment provider' AFTER method,
    ADD COLUMN provider_trade_no VARCHAR(100) NULL COMMENT 'Provider transaction number' AFTER provider,
    ADD COLUMN idempotency_key VARCHAR(64) NULL COMMENT 'Payment idempotency key' AFTER provider_trade_no,
    ADD UNIQUE KEY uk_pay_payment_idempotency (idempotency_key);
