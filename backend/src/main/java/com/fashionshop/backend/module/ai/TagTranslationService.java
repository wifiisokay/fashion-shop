package com.fashionshop.backend.module.ai;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Dịch tag hệ thống → tiếng Việt tự nhiên để nhúng vào AI prompt.
 * Đồng bộ với ProductTagLibrary — xóa tag đã merge, thêm mapping cho tag mới.
 */
@Service
public class TagTranslationService {

    // =====================================================
    // Occasion VI → system tag (detect từ message người dùng)
    // =====================================================
    private static final Map<String, String> OCCASION = new LinkedHashMap<>();

    // =====================================================
    // Style VI → system tag (detect từ message người dùng)
    // =====================================================
    private static final Map<String, String> STYLE = new LinkedHashMap<>();

    // =====================================================
    // Style tag → nhãn tiếng Việt (dùng trong AI prompt)
    // =====================================================
    private static final Map<String, String> STYLE_LABEL = new LinkedHashMap<>();

    // =====================================================
    // Occasion tag → nhãn tiếng Việt (dùng trong AI prompt)
    // =====================================================
    private static final Map<String, String> OCCASION_LABEL = new LinkedHashMap<>();

    static {
        // --- OCCASION detect map (VI keyword → system tag) ---
        OCCASION.put("đi làm", "work");
        OCCASION.put("công sở", "work");
        OCCASION.put("văn phòng", "work");
        OCCASION.put("hằng ngày", "daily");
        OCCASION.put("hàng ngày", "daily");
        OCCASION.put("mặc thường", "daily");
        OCCASION.put("đi chơi", "hangout");
        OCCASION.put("gặp bạn", "hangout");
        OCCASION.put("hẹn hò", "date");
        OCCASION.put("đi học", "school");
        OCCASION.put("trường", "school");
        OCCASION.put("dạo phố", "street");
        OCCASION.put("sự kiện", "event");
        OCCASION.put("tiệc", "event");
        OCCASION.put("du lịch", "travel");
        OCCASION.put("đi biển", "beach");
        OCCASION.put("thể thao", "sport");
        OCCASION.put("ngoài trời", "outdoor");
        OCCASION.put("mùa đông", "winter");
        OCCASION.put("trang trọng", "formal");

        // --- STYLE detect map (VI keyword → system tag) ---
        STYLE.put("basic", "casual");
        STYLE.put("thoải mái", "casual");
        STYLE.put("đơn giản", "casual");
        STYLE.put("thanh lịch", "smart-casual");
        STYLE.put("lịch sự", "smart-casual");
        STYLE.put("đường phố", "streetwear");
        STYLE.put("cá tính", "edgy");
        STYLE.put("tối giản", "minimal");
        STYLE.put("nữ tính", "feminine");
        STYLE.put("năng động", "sporty");
        STYLE.put("sang trọng", "elegant");
        STYLE.put("dễ thương", "cute");
        STYLE.put("xu hướng", "trendy");
        STYLE.put("thành thị", "urban");
        STYLE.put("cao cấp", "premium");
        STYLE.put("thêu", "embroidery");
        STYLE.put("oversize", "oversized");
        STYLE.put("rộng", "loose");

        // --- STYLE_LABEL (system tag → nhãn VI cho AI prompt) ---
        // Casual / Basic
        STYLE_LABEL.put("casual",      "Casual thường ngày");
        STYLE_LABEL.put("basic",       "Basic đơn giản");
        STYLE_LABEL.put("minimal",     "Tối giản");
        STYLE_LABEL.put("versatile",   "Đa năng");
        STYLE_LABEL.put("everyday",    "Phong cách hằng ngày");

        // Style đặc trưng
        STYLE_LABEL.put("streetwear",  "Streetwear đường phố");
        STYLE_LABEL.put("smart-casual","Smart casual lịch sự");
        STYLE_LABEL.put("formal",      "Formal trang trọng");
        STYLE_LABEL.put("elegant",     "Elegant sang trọng");
        STYLE_LABEL.put("romantic",    "Romantic lãng mạn");

        // Form / Silhouette
        STYLE_LABEL.put("fitted",      "Ôm vừa");
        STYLE_LABEL.put("loose",       "Rộng thoải mái");
        STYLE_LABEL.put("boxy",        "Dáng boxy vuông");
        STYLE_LABEL.put("crop",        "Dáng crop");
        STYLE_LABEL.put("oversized",   "Oversized rộng");
        STYLE_LABEL.put("layer",       "Dễ layering");
        STYLE_LABEL.put("slim",        "Slim ôm");
        STYLE_LABEL.put("straight",    "Straight thẳng");

        // Chất liệu / Detail
        STYLE_LABEL.put("embroidery",  "Thêu họa tiết");
        STYLE_LABEL.put("graphic",     "In họa tiết");
        STYLE_LABEL.put("textured",    "Vải texture");
        STYLE_LABEL.put("knit",        "Chất liệu dệt kim");
        STYLE_LABEL.put("eco",         "Eco thân thiện môi trường");
        STYLE_LABEL.put("denim",       "Denim");
        STYLE_LABEL.put("nylon",       "Nylon");
        STYLE_LABEL.put("detail",      "Chi tiết trang trí");

        // Trend / Vibe
        STYLE_LABEL.put("trendy",      "Trendy theo xu hướng");
        STYLE_LABEL.put("sporty",      "Sporty năng động");
        STYLE_LABEL.put("urban",       "Urban thành thị");
        STYLE_LABEL.put("premium",     "Premium cao cấp");
        STYLE_LABEL.put("cute",        "Cute dễ thương");
        STYLE_LABEL.put("feminine",    "Feminine nữ tính");
        STYLE_LABEL.put("edgy",        "Edgy cá tính");

        // --- OCCASION_LABEL (system tag → nhãn VI cho AI prompt) ---
        OCCASION_LABEL.put("daily",    "Hằng ngày");
        OCCASION_LABEL.put("school",   "Đi học");
        OCCASION_LABEL.put("work",     "Đi làm / Công sở");
        OCCASION_LABEL.put("hangout",  "Đi chơi");
        OCCASION_LABEL.put("date",     "Hẹn hò");
        OCCASION_LABEL.put("event",    "Sự kiện");
        OCCASION_LABEL.put("formal",   "Trang trọng");
        OCCASION_LABEL.put("travel",   "Du lịch");
        OCCASION_LABEL.put("sport",    "Thể thao");
        OCCASION_LABEL.put("outdoor",  "Ngoài trời");
        OCCASION_LABEL.put("beach",    "Đi biển");
        OCCASION_LABEL.put("street",   "Dạo phố");
        OCCASION_LABEL.put("winter",   "Mùa đông");
    }

    // =====================================================
    // Public API
    // =====================================================

    /** Detect occasion tag từ message người dùng. */
    public Optional<String> detectOccasionTag(String message) {
        return detect(message, OCCASION);
    }

    /** Detect style tag từ message người dùng. */
    public Optional<String> detectStyleTag(String message) {
        return detect(message, STYLE);
    }

    /** Lấy nhãn tiếng Việt cho style tag. */
    public String labelForStyle(String style) {
        return STYLE_LABEL.getOrDefault(style, style);
    }

    /** Lấy nhãn tiếng Việt cho occasion tag. */
    public String labelForOccasion(String occasion) {
        return OCCASION_LABEL.getOrDefault(occasion, occasion);
    }

    private Optional<String> detect(String message, Map<String, String> map) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return map.entrySet().stream()
            .filter(entry -> normalized.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst();
    }
}
