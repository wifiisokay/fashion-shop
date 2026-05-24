ALTER TABLE returns
ADD COLUMN previous_order_status VARCHAR(30) NULL AFTER status;

CREATE TABLE IF NOT EXISTS return_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    return_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    product_id BIGINT NULL,
    variant_id BIGINT NULL,
    product_name VARCHAR(255) NOT NULL,
    color_name VARCHAR(100) NULL,
    size VARCHAR(50) NULL,
    image_url TEXT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_return_items_return (return_id),
    KEY idx_return_items_order_item (order_item_id),
    KEY idx_return_items_product (product_id),
    KEY idx_return_items_variant (variant_id),
    CONSTRAINT fk_return_items_return
        FOREIGN KEY (return_id) REFERENCES returns(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_return_items_order_item
        FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT chk_return_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
