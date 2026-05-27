ALTER TABLE products
    ADD COLUMN sale_start_at DATETIME NULL AFTER sale_price,
    ADD COLUMN sale_end_at DATETIME NULL AFTER sale_start_at,
    ADD COLUMN low_stock_threshold INT NOT NULL DEFAULT 10 AFTER estimated_weight;
