ALTER TABLE categories
ADD COLUMN role VARCHAR(20) NULL
AFTER description;

UPDATE categories
SET role = 'ROOT'
WHERE
    slug IN (
        'thoi-trang-nam',
        'thoi-trang-nu'
    );

UPDATE categories
SET role = 'TOP'
WHERE
    slug IN (
        'ao-thun-nam',
        'ao-polo-nam',
        'ao-somi-nam',
        'ao-thun-nu',
        'ao-somi-nu'
    );

UPDATE categories
SET role = 'OUTER'
WHERE
    slug IN ('ao-khoac-nam', 'ao-khoac-nu');

UPDATE categories
SET role = 'BOTTOM'
WHERE
    slug IN (
        'quan-dai-nam',
        'quan-ngan-nam',
        'quan-dai-nu'
    );

UPDATE categories SET role = 'DRESS' WHERE slug = 'vay-va-dam';

-- Audit: categories missing role
SELECT id, name, slug FROM categories WHERE role IS NULL;

-- Audit: product counts by category role
SELECT c.role, COUNT(p.id) AS product_count
FROM products p
    LEFT JOIN categories c ON c.id = p.category_id
GROUP BY
    c.role
ORDER BY product_count DESC;
