package com.fashionshop.backend.module.product;

import java.util.Set;

/**
 * Tag vocabulary chuẩn hóa — nguồn sự thật duy nhất cho toàn bộ hệ thống.
 *
 * <p>Dùng bởi:
 * <ul>
 *   <li>{@link ProductServiceImpl} — validate khi admin tạo/sửa sản phẩm</li>
 *   <li>{@link ProductTagSuggestionService} — build constrained prompt cho Gemini</li>
 *   <li>{@code TagTranslationService} — translate tag sang tiếng Việt cho AI prompt</li>
 *   <li>AdminProductController GET /tag-library — FE load để build UI</li>
 * </ul>
 *
 * <p>Quy tắc merge từ seed data phân tích:
 * <ul>
 *   <li>"clean", "comfortable" → casual</li>
 *   <li>"everyday" → everyday (giữ lại, đủ phân biệt với daily)</li>
 *   <li>"tape-detail", "patch", "pocket-detail" → detail</li>
 *   <li>"oversize" → oversized (chuẩn hóa chính tả)</li>
 *   <li>"polo", "polo-dress" → loại bỏ (quá cụ thể, dùng category thay thế)</li>
 * </ul>
 */
public final class ProductTagLibrary {

    private ProductTagLibrary() {}

    // =====================================================
    // STYLE TAGS — 31 tags chuẩn hóa
    // =====================================================

    public static final Set<String> STYLE_TAGS = Set.of(
            // Casual / Basic
            "casual", "basic", "minimal", "versatile", "everyday",

            // Style đặc trưng
            "streetwear", "smart-casual", "formal", "elegant", "romantic",

            // Form / Silhouette
            "fitted", "loose", "boxy", "crop", "oversized", "layer", "slim", "straight",

            // Chất liệu / Detail
            "embroidery", "graphic", "textured", "knit", "eco",
            "denim", "nylon", "detail",

            // Trend / Vibe
            "trendy", "sporty", "urban", "premium", "cute", "feminine", "edgy"
    );

    // =====================================================
    // OCCASION TAGS — 13 tags chuẩn hóa
    // =====================================================

    public static final Set<String> OCCASION_TAGS = Set.of(
            // Ngày thường
            "daily", "school", "work", "hangout",

            // Đặc biệt
            "date", "event", "formal", "travel",

            // Ngoài trời / Hoạt động
            "sport", "outdoor", "beach", "street",

            // Season-specific
            "winter"
    );

    // =====================================================
    // FIT TYPES — 12 giá trị chuẩn hóa
    // =====================================================

    public static final Set<String> FIT_TYPES = Set.of(
            "Regular", "Fitted", "Loose", "Slim", "Straight",
            "Boxy", "Cropped", "Wide Leg", "Cocoon", "Carrot", "A-Line", "Relax"
    );

    // =====================================================
    // COLOR FAMILIES — 5 nhóm màu
    // =====================================================

    public static final Set<String> COLOR_FAMILIES = Set.of(
            "neutral", "cool", "warm", "earth", "mixed"
    );

    // =====================================================
    // SEASONS — 3 giá trị chuẩn hóa
    // =====================================================

    public static final Set<String> SEASONS = Set.of(
            "ALL_SEASON", "SPRING_SUMMER", "FALL_WINTER"
    );

    // =====================================================
    // LIMITS
    // =====================================================

    public static final int MAX_STYLE_TAGS = 4;
    public static final int MAX_OCCASION_TAGS = 4;

    // =====================================================
    // FIT TYPE GROUPS — dùng cho OutfitCandidateRetriever scoring
    // =====================================================

    /** Nhóm fitted: anchor LOOSE → ưu tiên FITTED_GROUP candidates */
    public static final Set<String> FIT_FITTED_GROUP = Set.of(
            "Regular", "Fitted", "Slim", "Cropped", "A-Line"
    );

    /** Nhóm loose: anchor FITTED → ưu tiên LOOSE_GROUP candidates */
    public static final Set<String> FIT_LOOSE_GROUP = Set.of(
            "Loose", "Boxy", "Wide Leg", "Cocoon", "Carrot", "Relax", "oversized"
    );

    /** Nhóm neutral: cân bằng, phối được cả 2 nhóm */
    public static final Set<String> FIT_NEUTRAL_GROUP = Set.of("Straight");
}
