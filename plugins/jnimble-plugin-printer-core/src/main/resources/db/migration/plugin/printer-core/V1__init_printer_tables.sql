CREATE TABLE IF NOT EXISTS prn_printer (
    id               VARCHAR(64)    NOT NULL PRIMARY KEY COMMENT 'Printer ID',
    name             VARCHAR(200)   NOT NULL COMMENT 'Printer display name',
    driver_id        VARCHAR(100)   NOT NULL COMMENT 'Driver identifier',
    type             VARCHAR(50)    NULL COMMENT 'Printer type: RECEIPT, KITCHEN',
    config_json      TEXT           NULL COMMENT 'Driver-specific configuration JSON',
    enabled          TINYINT(1)     NOT NULL DEFAULT 1 COMMENT 'Whether the printer is enabled',
    sort_order       INT            NOT NULL DEFAULT 0 COMMENT 'Display sort order',
    last_heartbeat_at DATETIME(3)   NULL COMMENT 'Last heartbeat timestamp',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_driver_id (driver_id),
    INDEX idx_enabled (enabled)
) COMMENT 'Printer device configuration';

CREATE TABLE IF NOT EXISTS prn_print_node (
    id              VARCHAR(64)    NOT NULL PRIMARY KEY COMMENT 'Node ID',
    node_name       VARCHAR(100)   NOT NULL COMMENT 'Unique node name for flow matching',
    display_name    VARCHAR(200)   NOT NULL COMMENT 'Human-readable node display name',
    printer_id      VARCHAR(64)    NULL COMMENT 'Associated printer ID',
    template_view   VARCHAR(200)   NULL COMMENT 'Thymeleaf template for print content',
    enabled         TINYINT(1)     NOT NULL DEFAULT 1 COMMENT 'Whether the print node is enabled',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_name (node_name),
    INDEX idx_printer_id (printer_id)
) COMMENT 'Print flow node configuration';

INSERT INTO prn_print_node (id, node_name, display_name, printer_id, template_view, enabled) VALUES
    (UUID(), 'order.confirmed', '订单确认（后厨）', NULL, NULL, 1),
    (UUID(), 'order.item.added', '加菜', NULL, NULL, 1),
    (UUID(), 'order.settled', '结账完成', NULL, NULL, 1),
    (UUID(), 'order.cancelled', '订单取消', NULL, NULL, 0),
    (UUID(), 'order.refund', '退菜', NULL, NULL, 0);

CREATE TABLE IF NOT EXISTS prn_print_job (
    id              VARCHAR(64)    NOT NULL PRIMARY KEY COMMENT 'Job ID',
    order_id        VARCHAR(64)    NOT NULL COMMENT 'Associated order ID',
    printer_id      VARCHAR(64)    NOT NULL COMMENT 'Target printer ID',
    node_name       VARCHAR(100)   NULL COMMENT 'Print node name that triggered this job',
    type            VARCHAR(50)    NOT NULL COMMENT 'Print type: RECEIPT, KITCHEN',
    content         TEXT           NOT NULL COMMENT 'Print content',
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PRINTING, SUCCESS, FAILED',
    retry_count     INT            NOT NULL DEFAULT 0 COMMENT 'Number of retries attempted',
    max_retries     INT            NOT NULL DEFAULT 3 COMMENT 'Maximum retry count',
    error_message   VARCHAR(1000)  NULL COMMENT 'Last error message',
    driver_id       VARCHAR(100)   NOT NULL COMMENT 'Driver used for this job',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    printed_at      DATETIME(3)    NULL COMMENT 'Actual print completion time',
    INDEX idx_order_id (order_id),
    INDEX idx_printer_id (printer_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) COMMENT 'Print job queue';
