ALTER TABLE categories
ADD CONSTRAINT chk_categories_child_role
CHECK (parent_id IS NULL OR role IS NOT NULL);
