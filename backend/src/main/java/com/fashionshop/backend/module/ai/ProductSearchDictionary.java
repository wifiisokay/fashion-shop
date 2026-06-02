package com.fashionshop.backend.module.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProductSearchDictionary {

    private static final Map<String, List<String>> PRODUCT_TERMS = new LinkedHashMap<>();
    private static final Map<String, String> OCCASIONS = new LinkedHashMap<>();
    private static final List<String> ATTRIBUTE_TERMS = List.of(
        "cotton", "linen", "kaki", "len", "nỉ", "ni", "cardigan", "blazer", "jacket",
        "oversized", "oversize", "slim", "regular", "relaxed", "fitted", "loose",
        "mùa hè", "mua he", "mùa đông", "mua dong", "xuân hè", "xuan he", "thu đông", "thu dong"
    );

    static {
        concept("ao thun", "áo thun", "ao thun", "áo phông", "ao phong", "t-shirt", "tshirt", "tee");
        concept("ao polo", "áo polo", "ao polo", "polo", "áo có cổ", "ao co co");
        concept("ao so mi", "áo sơ mi", "ao so mi", "sơ mi", "so mi", "shirt");
        concept("quan jean", "quần jean", "quan jean", "jeans", "denim");
        concept("quan short", "quần short", "quan short", "short", "quần ngắn", "quan ngan", "quần đùi", "quan dui");
        concept("vay dam", "váy", "vay", "đầm", "dam", "chân váy", "chan vay", "dress", "skirt");
        concept("hoodie", "hoodie", "áo nỉ", "ao ni", "áo khoác", "ao khoac");
        concept("quan tay", "quần tây", "quan tay", "trousers");

        occasion("work", "công sở", "cong so", "văn phòng", "van phong", "đi làm", "di lam");
        occasion("school", "đi học", "di hoc");
        occasion("hangout", "đi chơi", "di choi", "cafe");
        occasion("street", "dạo phố", "dao pho");
        occasion("date", "hẹn hò", "hen ho");
        occasion("travel", "du lịch", "du lich");
        occasion("sport", "thể thao", "the thao");
        occasion("outdoor", "ngoài trời", "ngoai troi");
        occasion("beach", "đi biển", "di bien");
        occasion("event", "sự kiện", "su kien");
        occasion("formal", "trang trọng", "trang trong");
    }

    private ProductSearchDictionary() {
    }

    public static List<String> productTerms(String text) {
        String normalized = VietnameseTextNormalizer.padded(text);
        Set<String> terms = new LinkedHashSet<>();
        PRODUCT_TERMS.forEach((canonical, aliases) -> {
            if (aliases.stream().map(VietnameseTextNormalizer::normalize)
                .anyMatch(alias -> normalized.contains(" " + alias + " "))) {
                aliases.forEach(alias -> addTerm(terms, alias));
                addTerm(terms, canonical);
            }
        });
        ATTRIBUTE_TERMS.stream()
            .filter(term -> normalized.contains(" " + VietnameseTextNormalizer.normalize(term) + " "))
            .forEach(term -> addTerm(terms, term));
        return new ArrayList<>(terms);
    }

    public static String occasionTag(String text) {
        String normalized = VietnameseTextNormalizer.padded(text);
        return OCCASIONS.entrySet().stream()
            .filter(entry -> normalized.contains(" " + VietnameseTextNormalizer.normalize(entry.getKey()) + " "))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private static void concept(String canonical, String... aliases) {
        PRODUCT_TERMS.put(canonical, List.of(aliases));
    }

    private static void occasion(String tag, String... aliases) {
        for (String alias : aliases) {
            OCCASIONS.put(alias, tag);
        }
    }

    private static void addTerm(Set<String> terms, String term) {
        if (term != null && !term.isBlank()) {
            terms.add(term.toLowerCase());
            terms.add(VietnameseTextNormalizer.normalize(term));
        }
    }
}
