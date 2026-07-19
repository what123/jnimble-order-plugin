ALTER TABLE ord_table
    ADD COLUMN table_name VARCHAR(100) NULL COMMENT 'Dining table display name' AFTER id,
    ADD COLUMN min_party_size INT NOT NULL DEFAULT 1 COMMENT 'Minimum supported party size' AFTER table_name,
    ADD COLUMN max_party_size INT NOT NULL DEFAULT 4 COMMENT 'Maximum supported party size' AFTER min_party_size,
    ADD COLUMN scan_code VARCHAR(32) NULL COMMENT 'Stable sequence for QR table scanning' AFTER max_party_size;

UPDATE ord_table
SET table_name = CASE
        WHEN code IS NULL OR TRIM(code) = '' THEN CONCAT('餐桌', id)
        ELSE code
    END,
    min_party_size = 1,
    max_party_size = CASE
        WHEN seat_count IS NULL OR seat_count < 1 THEN 4
        ELSE seat_count
    END,
    scan_code = CONCAT('TB', LPAD(id, 8, '0'));

ALTER TABLE ord_table
    MODIFY COLUMN table_name VARCHAR(100) NOT NULL COMMENT 'Dining table display name',
    MODIFY COLUMN scan_code VARCHAR(32) NOT NULL COMMENT 'Stable sequence for QR table scanning',
    ADD UNIQUE KEY uk_ord_table_scan_code (scan_code);
