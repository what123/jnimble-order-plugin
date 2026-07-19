ALTER TABLE ord_table
    ADD COLUMN operational_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE, CLEANING, DISABLED, MAINTENANCE' AFTER status,
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version' AFTER operational_status,
    ADD COLUMN last_turned_at DATETIME NULL COMMENT 'Last completed turnover time' AFTER version;

CREATE TABLE IF NOT EXISTS ord_dining_session (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_no       VARCHAR(32)  NOT NULL COMMENT 'Readable dining session number',
    guest_count      INT          NOT NULL COMMENT 'Current guest count',
    status           VARCHAR(20)  NOT NULL COMMENT 'HELD, DINING, CHECKING, PAID, CLOSED, CANCELLED, EXPIRED',
    primary_table_id BIGINT       NOT NULL COMMENT 'Current primary table',
    reservation_id  BIGINT       NULL COMMENT 'Reservation source',
    customer_ref    VARCHAR(100) NULL COMMENT 'External customer reference',
    hold_expires_at DATETIME     NULL,
    opened_by       VARCHAR(50)  NULL,
    opened_at       DATETIME     NOT NULL,
    checking_at     DATETIME     NULL,
    paid_at         DATETIME     NULL,
    closed_at       DATETIME     NULL,
    version          INT          NOT NULL DEFAULT 0,
    remark           VARCHAR(500) NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ord_dining_session_no (session_no),
    INDEX idx_ord_dining_session_table (primary_table_id),
    INDEX idx_ord_dining_session_status (status)
) COMMENT 'Dining session';

CREATE TABLE IF NOT EXISTS ord_table_occupancy (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id          BIGINT       NOT NULL,
    session_id        BIGINT       NOT NULL,
    mode              VARCHAR(20)  NOT NULL COMMENT 'PRIMARY, SHARED, COMBINED',
    seat_count        INT          NOT NULL DEFAULT 0,
    status            VARCHAR(20)  NOT NULL COMMENT 'ACTIVE, RELEASED',
    occupied_at       DATETIME     NOT NULL,
    released_at       DATETIME     NULL,
    release_reason    VARCHAR(20)  NULL,
    from_occupancy_id BIGINT       NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ord_occupancy_table_status (table_id, status),
    INDEX idx_ord_occupancy_session_status (session_id, status)
) COMMENT 'Table occupancy history';

CREATE TABLE IF NOT EXISTS ord_checkout (
    checkout_no       VARCHAR(32)    PRIMARY KEY,
    session_id        BIGINT         NOT NULL,
    order_id          BIGINT         NOT NULL,
    scope             VARCHAR(20)    NOT NULL DEFAULT 'FULL',
    status            VARCHAR(20)    NOT NULL COMMENT 'CREATED, PAYING, PAID, FAILED, CANCELLED',
    original_amount   DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    discount_amount   DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    payable_amount    DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    paid_amount       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    change_amount     DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    payment_method    VARCHAR(20)    NULL,
    payment_id        VARCHAR(32)    NULL,
    idempotency_key   VARCHAR(64)    NOT NULL,
    created_by        VARCHAR(50)    NULL,
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at           DATETIME       NULL,
    updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ord_checkout_idempotency (idempotency_key),
    INDEX idx_ord_checkout_session (session_id),
    INDEX idx_ord_checkout_order (order_id)
) COMMENT 'Dining checkout snapshot';

CREATE TABLE IF NOT EXISTS ord_table_operation_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id     BIGINT       NULL,
    table_id       BIGINT       NULL,
    operation      VARCHAR(40)  NOT NULL,
    before_value   VARCHAR(1000) NULL,
    after_value    VARCHAR(1000) NULL,
    reason         VARCHAR(500) NULL,
    operator       VARCHAR(50)  NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ord_table_log_session (session_id),
    INDEX idx_ord_table_log_table (table_id),
    INDEX idx_ord_table_log_created (created_at)
) COMMENT 'Table operation audit log';
