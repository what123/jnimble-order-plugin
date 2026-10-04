ALTER TABLE menu_item ADD COLUMN force_selected TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=not forced, 1=force selected (e.g. mandatory tea/water)';
