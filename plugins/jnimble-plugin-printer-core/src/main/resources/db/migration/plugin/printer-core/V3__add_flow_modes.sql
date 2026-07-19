ALTER TABLE prn_flow_definition
    ADD COLUMN billing_mode VARCHAR(20) NOT NULL DEFAULT 'POSTPAID' AFTER id;

ALTER TABLE prn_print_node
    ADD COLUMN operation_mode VARCHAR(20) NOT NULL DEFAULT 'PAPERLESS' AFTER template_view,
    ADD COLUMN confirmation_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL' AFTER operation_mode;

UPDATE prn_print_node
SET operation_mode = 'PRINT'
WHERE printer_id IS NOT NULL;

UPDATE prn_print_node
SET display_name = '前台确认订单'
WHERE node_name = 'order.confirmed';

INSERT IGNORE INTO prn_print_node (
    id,
    node_name,
    display_name,
    printer_id,
    template_view,
    operation_mode,
    confirmation_mode,
    enabled
) VALUES
    (UUID(), 'order.kitchen.confirmed', '后厨确认订单', NULL, NULL, 'PAPERLESS', 'MANUAL', 1),
    (UUID(), 'order.served', '完成出菜（待取餐/待上桌）', NULL, NULL, 'PAPERLESS', 'MANUAL', 1);
