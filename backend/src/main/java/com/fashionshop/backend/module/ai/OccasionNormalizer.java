package com.fashionshop.backend.module.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class OccasionNormalizer {

    private static final Map<String, String> OCCASION = new LinkedHashMap<>();
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        OCCASION.put("di lam", "work");
        OCCASION.put("cong so", "work");
        OCCASION.put("office", "work");
        OCCASION.put("di hoc", "school");
        OCCASION.put("truong", "school");
        OCCASION.put("dao pho", "street");
        OCCASION.put("di choi", "hangout");
        OCCASION.put("cafe", "hangout");
        OCCASION.put("coffee", "hangout");
        OCCASION.put("hen ho", "date");
        OCCASION.put("su kien", "event");
        OCCASION.put("tiec", "event");
        OCCASION.put("du lich", "travel");
        OCCASION.put("di bien", "beach");
        OCCASION.put("the thao", "sport");
        OCCASION.put("ngoai troi", "outdoor");
        OCCASION.put("mua dong", "winter");
        OCCASION.put("hang ngay", "daily");
        OCCASION.put("thuong ngay", "daily");

        LABELS.put("work", "đi làm / công sở");
        LABELS.put("school", "đi học");
        LABELS.put("street", "dạo phố");
        LABELS.put("hangout", "đi chơi / cafe");
        LABELS.put("date", "hẹn hò");
        LABELS.put("event", "sự kiện");
        LABELS.put("travel", "du lịch");
        LABELS.put("beach", "đi biển");
        LABELS.put("sport", "thể thao");
        LABELS.put("outdoor", "ngoài trời");
        LABELS.put("winter", "mùa đông");
        LABELS.put("daily", "hằng ngày");
    }

    public Optional<String> detectTag(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String normalized = VietnameseTextNormalizer.normalize(message);
        return OCCASION.entrySet().stream()
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
}
