ALTER TABLE orders
ADD COLUMN completed_at DATETIME NULL
AFTER delivered_at;

UPDATE orders
SET
    completed_at = updated_at
WHERE
    status = 'COMPLETED'
    AND completed_at IS NULL;

ALTER TABLE returns
ADD COLUMN received_at DATETIME NULL,
ADD COLUMN refunded_at DATETIME NULL;

UPDATE returns SET status = 'REQUESTED' WHERE status = 'PENDING';

-- Trước khi chạy unique constraint, kiểm tra dữ liệu trùng order_id:
-- SELECT order_id, COUNT(*)
-- FROM returns
-- GROUP BY order_id
-- HAVING COUNT(*) > 1;

ALTER TABLE returns
ADD CONSTRAINT uq_returns_order UNIQUE (order_id);