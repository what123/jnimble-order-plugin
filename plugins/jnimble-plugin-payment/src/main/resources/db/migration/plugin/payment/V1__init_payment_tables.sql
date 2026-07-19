CREATE TABLE IF NOT EXISTS pay_payment (
    id              VARCHAR(32)     NOT NULL PRIMARY KEY COMMENT 'Primary key',
    order_id        VARCHAR(32)     NOT NULL COMMENT 'Order ID',
    method          VARCHAR(20)     NOT NULL COMMENT 'Payment method: CASH/WECHAT/ALIPAY/CARD',
    amount          DECIMAL(12,2)   NOT NULL COMMENT 'Payment amount',
    received_amount DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT 'Received amount',
    change_amount   DECIMAL(12,2)   NOT NULL DEFAULT 0 COMMENT 'Change amount',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PAID' COMMENT 'Payment status',
    operator        VARCHAR(50)     NULL COMMENT 'Operator',
    paid_at         DATETIME        NULL COMMENT 'Payment time',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Payment records';
