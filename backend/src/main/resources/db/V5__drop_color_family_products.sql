SET @drop_products_color_family = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'products'
        AND column_name = 'color_family'
    ),
    'ALTER TABLE products DROP COLUMN color_family',
    'SELECT 1'
  )
);

PREPARE drop_products_color_family_stmt FROM @drop_products_color_family;
EXECUTE drop_products_color_family_stmt;
DEALLOCATE PREPARE drop_products_color_family_stmt;
