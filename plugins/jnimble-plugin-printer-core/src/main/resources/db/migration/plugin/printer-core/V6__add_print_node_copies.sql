ALTER TABLE prn_print_node
    ADD COLUMN copies INT NOT NULL DEFAULT 1 AFTER confirmation_mode;
