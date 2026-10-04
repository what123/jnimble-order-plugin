-- 飞鹅云打印订单ID回写字段(Open_printMsg 返回的 data 值,用于后续查询打印状态)
ALTER TABLE prn_print_job
    ADD COLUMN external_order_id VARCHAR(128) NULL COMMENT '外部打印平台返回的订单ID' AFTER driver_id;
