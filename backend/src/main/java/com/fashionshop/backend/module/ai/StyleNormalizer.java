package com.fashionshop.backend.module.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class StyleNormalizer {

    private static final Map<String, String> STYLE = new LinkedHashMap<>();
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        STYLE.put("basic", "casual");
        STYLE.put("don gian", "basic");
        STYLE.put("thanh lich", "smart-casual");
        STYLE.put("lich su", "smart-casual");
        STYLE.put("duong pho", "streetwear");
        STYLE.put("ca tinh", "edgy");
        STYLE.put("toi gian", "minimal");
        STYLE.put("nu tinh", "feminine");
        STYLE.put("nang dong", "sporty");
        STYLE.put("sang trong", "elegant");
        STYLE.put("de thuong", "cute");
        STYLE.put("xu huong", "trendy");
        STYLE.put("thanh thi", "urban");
        STYLE.put("cao cap", "premium");
        STYLE.put("oversize", "oversized");
        STYLE.put("rong", "loose");

        LABELS.put("casual", "Casual thuong ngay");
        LABELS.put("basic", "Basic don gian");
        LABELS.put("smart-casual", "Smart casual lich su");
        LABELS.put("streetwear", "Streetwear duong pho");
        LABELS.put("edgy", "Edgy ca tinh");
        LABELS.put("minimal", "Toi gian");
        LABELS.put("feminine", "Feminine nu tinh");
        LABELS.put("sporty", "Sporty nang dong");
        LABELS.put("elegant", "Elegant sang trong");
        LABELS.put("cute", "Cute de thuong");
        LABELS.put("trendy", "Trendy xu huong");
        LABELS.put("urban", "Urban thanh thi");
        LABELS.put("premium", "Premium cao cap");
        LABELS.put("oversized", "Oversized rong");
        LABELS.put("loose", "Rong thoai mai");
    }

    public Optional<String> detectTag(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String normalized = VietnameseTextNormalizer.normalize(message);
        return STYLE.entrySet().stream()
            .filter(entry -> normalized.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    public String labelFor(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        return LABELS.getOrDefault(tag, tag);
    }

    public String inferForOccasion(String occasionTag) {
        if (occasionTag == null) {
            return null;
        }
        return switch (occasionTag) {
            case "work", "event", "formal" -> "smart-casual";
            case "street", "hangout" -> "streetwear";
            case "sport", "outdoor" -> "sporty";
            default -> "casual";
        };
    }
}
