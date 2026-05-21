-- ============================================================
-- V3: Chuẩn hóa tag data cũ trong bảng products
-- Dựa trên phân tích seed_product_data.sql vs ProductTagLibrary
-- Chạy script này trên DB trước khi restart application
-- ============================================================

-- ============================================================
-- 1. style_tags — merge synonyms & loại bỏ tag không hợp lệ
-- ============================================================

-- "oversize" → "oversized" (chuẩn hóa chính tả)
UPDATE products
SET style_tags = JSON_REPLACE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'oversize')), ']'),
    'oversized')
WHERE JSON_SEARCH(style_tags, 'one', 'oversize') IS NOT NULL;

-- "relax" → vẫn hợp lệ trong FIT_TYPES nhưng KHÔNG hợp lệ trong style_tags → xóa
UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'relax')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'relax') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'relax') != 'null';

-- "clean" → loại bỏ (merge vào "minimal" nếu chưa có, hoặc bỏ hẳn)
UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'clean')), ']')),
    '$', 'minimal')
WHERE JSON_SEARCH(style_tags, 'one', 'clean') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'minimal') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'clean')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'clean') IS NOT NULL;

-- "comfortable" → loại bỏ (merge vào "casual" nếu chưa có)
UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'comfortable')), ']')),
    '$', 'casual')
WHERE JSON_SEARCH(style_tags, 'one', 'comfortable') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'casual') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'comfortable')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'comfortable') IS NOT NULL;

-- "tape-detail", "patch", "pocket-detail", "leather-detail", "stripe-detail" → "detail"
-- (xử lý từng tag)
UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'tape-detail')), ']')),
    '$', 'detail')
WHERE JSON_SEARCH(style_tags, 'one', 'tape-detail') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'detail') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'tape-detail')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'tape-detail') IS NOT NULL;

UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'patch')), ']')),
    '$', 'detail')
WHERE JSON_SEARCH(style_tags, 'one', 'patch') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'detail') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'patch')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'patch') IS NOT NULL;

UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'pocket-detail')), ']')),
    '$', 'detail')
WHERE JSON_SEARCH(style_tags, 'one', 'pocket-detail') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'detail') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'pocket-detail')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'pocket-detail') IS NOT NULL;

UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'leather-detail')), ']')),
    '$', 'detail')
WHERE JSON_SEARCH(style_tags, 'one', 'leather-detail') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'detail') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'leather-detail')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'leather-detail') IS NOT NULL;

UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'stripe-detail')), ']')),
    '$', 'detail')
WHERE JSON_SEARCH(style_tags, 'one', 'stripe-detail') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'detail') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'stripe-detail')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'stripe-detail') IS NOT NULL;

-- Loại bỏ hoàn toàn các tag không thuộc library và không có mapping tốt:
-- "polo", "polo-dress", "shirt", "hoodie", "bomber", "puffer" (quá cụ thể - dùng category)
-- "chic" → elegant, "modern" → minimal, "classic" → minimal, "retro" → trendy, "must-have" → versatile
-- "colorblock", "stripe", "floral" → graphic
-- "summer" → không hợp lệ (season riêng), "warm" → không hợp lệ (color_family riêng)

-- "chic" → "elegant"
UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'chic')), ']')),
    '$', 'elegant')
WHERE JSON_SEARCH(style_tags, 'one', 'chic') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'elegant') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'chic')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'chic') IS NOT NULL;

-- "modern", "classic" → "minimal"
UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'modern')), ']')),
    '$', 'minimal')
WHERE JSON_SEARCH(style_tags, 'one', 'modern') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'minimal') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'modern')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'modern') IS NOT NULL;

UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'classic')), ']')),
    '$', 'minimal')
WHERE JSON_SEARCH(style_tags, 'one', 'classic') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'minimal') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'classic')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'classic') IS NOT NULL;

-- "retro" → "trendy"
UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'retro')), ']')),
    '$', 'trendy')
WHERE JSON_SEARCH(style_tags, 'one', 'retro') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'trendy') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'retro')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'retro') IS NOT NULL;

-- "must-have" → "versatile"
UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'must-have')), ']')),
    '$', 'versatile')
WHERE JSON_SEARCH(style_tags, 'one', 'must-have') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'versatile') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'must-have')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'must-have') IS NOT NULL;

-- "colorblock", "stripe", "floral" → "graphic"
UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'colorblock')), ']')),
    '$', 'graphic')
WHERE JSON_SEARCH(style_tags, 'one', 'colorblock') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'graphic') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'colorblock')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'colorblock') IS NOT NULL;

UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'stripe')), ']')),
    '$', 'graphic')
WHERE JSON_SEARCH(style_tags, 'one', 'stripe') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'graphic') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'stripe')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'stripe') IS NOT NULL;

UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'floral')), ']')),
    '$', 'graphic')
WHERE JSON_SEARCH(style_tags, 'one', 'floral') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'graphic') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'floral')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'floral') IS NOT NULL;

-- Loại bỏ hoàn toàn (không có mapping tốt hoặc đã có trong category/field riêng):
-- polo, polo-dress, shirt, hoodie, bomber, puffer, windbreaker → category đã cover
-- summer, warm → season/color_family field riêng
-- cropped → đã có "crop" trong library (chuẩn hóa)
-- regular → FIT_TYPE, không phải style_tag

UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'polo')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'polo') IS NOT NULL;
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'polo-dress')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'polo-dress') IS NOT NULL;
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'shirt')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'shirt') IS NOT NULL;
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'hoodie')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'hoodie') IS NOT NULL;
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'bomber')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'bomber') IS NOT NULL;
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'puffer')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'puffer') IS NOT NULL;
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'windbreaker')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'windbreaker') IS NOT NULL;
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'summer')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'summer') IS NOT NULL;
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'warm')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'warm') IS NOT NULL;
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'regular')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'regular') IS NOT NULL;

-- "cropped" → "crop" (chuẩn hóa tên)
UPDATE products
SET style_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'cropped')), ']')),
    '$', 'crop')
WHERE JSON_SEARCH(style_tags, 'one', 'cropped') IS NOT NULL
  AND JSON_SEARCH(style_tags, 'one', 'crop') IS NULL;

UPDATE products
SET style_tags = JSON_REMOVE(style_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'cropped')), ']'))
WHERE JSON_SEARCH(style_tags, 'one', 'cropped') IS NOT NULL;

-- "wide-leg" → style_tags không hợp lệ (đây là FIT_TYPE), loại bỏ
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'wide-leg')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'wide-leg') IS NOT NULL;

-- "a-line" → style_tags không hợp lệ (đây là FIT_TYPE "A-Line"), loại bỏ
UPDATE products SET style_tags = JSON_REMOVE(style_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(style_tags, 'one', 'a-line')), ']')) WHERE JSON_SEARCH(style_tags, 'one', 'a-line') IS NOT NULL;

-- ============================================================
-- 2. occasion_tags — "everyday" → "daily"
-- ============================================================

UPDATE products
SET occasion_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(occasion_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(occasion_tags, 'one', 'everyday')), ']')),
    '$', 'daily')
WHERE JSON_SEARCH(occasion_tags, 'one', 'everyday') IS NOT NULL
  AND JSON_SEARCH(occasion_tags, 'one', 'daily') IS NULL;

UPDATE products
SET occasion_tags = JSON_REMOVE(occasion_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(occasion_tags, 'one', 'everyday')), ']'))
WHERE JSON_SEARCH(occasion_tags, 'one', 'everyday') IS NOT NULL;

-- "office" → "work"
UPDATE products
SET occasion_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(occasion_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(occasion_tags, 'one', 'office')), ']')),
    '$', 'work')
WHERE JSON_SEARCH(occasion_tags, 'one', 'office') IS NOT NULL
  AND JSON_SEARCH(occasion_tags, 'one', 'work') IS NULL;

UPDATE products
SET occasion_tags = JSON_REMOVE(occasion_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(occasion_tags, 'one', 'office')), ']'))
WHERE JSON_SEARCH(occasion_tags, 'one', 'office') IS NOT NULL;

-- "interview" → "formal"
UPDATE products
SET occasion_tags = JSON_ARRAY_APPEND(
    JSON_REMOVE(occasion_tags, CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(occasion_tags, 'one', 'interview')), ']')),
    '$', 'formal')
WHERE JSON_SEARCH(occasion_tags, 'one', 'interview') IS NOT NULL
  AND JSON_SEARCH(occasion_tags, 'one', 'formal') IS NULL;

UPDATE products
SET occasion_tags = JSON_REMOVE(occasion_tags,
    CONCAT('$[', JSON_UNQUOTE(JSON_SEARCH(occasion_tags, 'one', 'interview')), ']'))
WHERE JSON_SEARCH(occasion_tags, 'one', 'interview') IS NOT NULL;

-- ============================================================
-- 3. fit_type — chuẩn hóa "wide leg" → "Wide Leg", "a-line" → "A-Line"
-- ============================================================

UPDATE products SET fit_type = 'Wide Leg' WHERE fit_type IN ('wide leg', 'wide-leg', 'Wide leg');
UPDATE products SET fit_type = 'A-Line'   WHERE fit_type IN ('a-line', 'a line', 'A-line');
UPDATE products SET fit_type = 'Straight' WHERE fit_type IN ('straight', 'Straight Leg');
UPDATE products SET fit_type = 'Relax'    WHERE fit_type IN ('relax', 'Relaxed');
UPDATE products SET fit_type = 'Cocoon'   WHERE fit_type IN ('cocoon');
UPDATE products SET fit_type = 'Carrot'   WHERE fit_type IN ('carrot');

-- ============================================================
-- VERIFY — chạy sau khi migration để kiểm tra
-- ============================================================
-- SELECT COUNT(*) as remaining_invalid FROM products
-- WHERE JSON_OVERLAPS(style_tags, '["clean","comfortable","tape-detail","patch","pocket-detail","polo","polo-dress","shirt","hoodie","bomber","puffer","everyday","oversize","modern","classic","retro","must-have","colorblock","stripe","floral","chic","wide-leg","a-line","summer","warm","regular","leather-detail","stripe-detail","windbreaker"]');
