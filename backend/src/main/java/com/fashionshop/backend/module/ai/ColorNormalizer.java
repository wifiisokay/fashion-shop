package com.fashionshop.backend.module.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ColorNormalizer {

    private static final Set<String> DARK_TONES = Set.of("black", "navy", "brown", "olive");
    private static final Set<String> LIGHT_TONES = Set.of("white", "beige", "pink", "yellow");
    private static final Set<String> MEDIUM_TONES = Set.of("gray", "blue", "green", "red", "orange");

    private static final Set<String> NEUTRALS = Set.of("black", "white", "gray", "beige");
    private static final Set<String> EARTHS = Set.of("brown", "olive");
    private static final Set<String> COOLS = Set.of("navy", "blue", "green");
    private static final Set<String> WARMS = Set.of("pink", "red", "yellow", "orange");

    /**
     * Normalize any string to one of the 13 baseline colors or "unknown".
     */
    public static String normalizeColor(String colorName) {
        if (colorName == null || colorName.isBlank()) {
            return "unknown";
        }
        String lower = VietnameseTextNormalizer.normalize(colorName).trim().toLowerCase(Locale.ROOT);
        
        if (lower.contains("den") || lower.contains("black") || lower.contains("huyen")) {
            return "black";
        }
        if (lower.contains("trang") || lower.contains("white") || lower.contains("bach")) {
            return "white";
        }
        if (lower.contains("xam") || lower.contains("ghi") || lower.contains("gray") || lower.contains("grey")) {
            return "gray";
        }
        if (lower.contains("be") || lower.contains("kem") || lower.contains("cream") || lower.contains("beige") || lower.contains("sua")) {
            return "beige";
        }
        if (lower.contains("navy") || lower.contains("xanh dam") || lower.contains("tim than") || lower.contains("xanh den")) {
            return "navy";
        }
        if (lower.contains("olive") || lower.contains("reu")) {
            return "olive";
        }
        if (lower.contains("xanh la") || lower.contains("green") || lower.contains("mint") || lower.contains("luc")) {
            return "green";
        }
        if (lower.contains("xanh") || lower.contains("blue") || lower.contains("sky") || lower.contains("lam")) {
            return "blue";
        }
        if (lower.contains("hong") || lower.contains("pink") || lower.contains("dao")) {
            return "pink";
        }
        if (lower.contains("do") || lower.contains("red") || lower.contains("burgundy") || lower.contains("ruou") || lower.contains("hoa hien")) {
            return "red";
        }
        if (lower.contains("vang") || lower.contains("yellow") || lower.contains("gold") || lower.contains("chanh")) {
            return "yellow";
        }
        if (lower.contains("cam") || lower.contains("orange")) {
            return "orange";
        }
        if (lower.contains("nau") || lower.contains("brown") || lower.contains("camel") || lower.contains("bo")) {
            return "brown";
        }
        return "unknown";
    }

    public static String getColorTone(String normalizedColor) {
        if (DARK_TONES.contains(normalizedColor)) return "dark";
        if (LIGHT_TONES.contains(normalizedColor)) return "light";
        if (MEDIUM_TONES.contains(normalizedColor)) return "medium";
        return "medium"; // fallback
    }

    public static String getColorTemperature(String normalizedColor) {
        if (NEUTRALS.contains(normalizedColor)) return "neutral";
        if (EARTHS.contains(normalizedColor)) return "earth";
        if (COOLS.contains(normalizedColor)) return "cool";
        if (WARMS.contains(normalizedColor)) return "warm";
        return "neutral"; // fallback
    }

    /**
     * Compatibility score (0.0 to 30.0).
     */
    public static double calculateColorScore(ChatProductCard base, ChatProductCard candidate) {
        String baseName = base.getColorName();
        String candName = candidate.getColorName();
        String normA = normalizeColor(baseName);
        String normB = normalizeColor(candName);

        if ("unknown".equals(normA) || "unknown".equals(normB)) {
            return fallbackColorScore(base.getColorFamily(), candidate.getColorFamily());
        }

        // Rule: same color
        if (normA.equals(normB)) return 30.0;

        String tempA = getColorTemperature(normA);
        String tempB = getColorTemperature(normB);

        // Rule: monochrome / same temperature family
        if (tempA.equals(tempB)) return 30.0;

        // Rule: neutral + any = tốt
        if ("neutral".equals(tempA) || "neutral".equals(tempB)) return 30.0;

        // Rule: specific optimal pairs
        if (isPair(normA, normB, "black", "white") || isPair(normA, normB, "black", "gray") || isPair(normA, normB, "black", "beige")) return 30.0;
        if (isPair(normA, normB, "white", "black") || isPair(normA, normB, "white", "gray") || isPair(normA, normB, "white", "navy") || isPair(normA, normB, "white", "beige") || isPair(normA, normB, "white", "brown")) return 30.0;
        if (isPair(normA, normB, "navy", "beige") || isPair(normA, normB, "navy", "white") || isPair(normA, normB, "navy", "gray")) return 30.0;
        if (isPair(normA, normB, "beige", "navy") || isPair(normA, normB, "beige", "brown") || isPair(normA, normB, "beige", "white") || isPair(normA, normB, "beige", "black")) return 30.0;
        if (isPair(normA, normB, "gray", "black") || isPair(normA, normB, "gray", "white") || isPair(normA, normB, "gray", "navy") || isPair(normA, normB, "gray", "red")) return 30.0;
        if (isPair(normA, normB, "olive", "beige") || isPair(normA, normB, "olive", "white")) return 30.0;

        // Rule: dark + light neutral = tốt để cân bằng
        String toneA = getColorTone(normA);
        String toneB = getColorTone(normB);
        if (isDarkAndLightNeutral(normA, toneA, normB, toneB)) return 30.0;

        // Rule: cool + warm = tránh/phạt
        if (("cool".equals(tempA) && "warm".equals(tempB)) || ("warm".equals(tempA) && "cool".equals(tempB))) {
            return 5.0; // avoid
        }

        return 15.0; // default/medium compatibility
    }

    private static boolean isPair(String a, String b, String target1, String target2) {
        return (a.equals(target1) && b.equals(target2)) || (a.equals(target2) && b.equals(target1));
    }

    private static boolean isDarkAndLightNeutral(String normA, String toneA, String normB, String toneB) {
        boolean darkA = "dark".equals(toneA);
        boolean darkB = "dark".equals(toneB);
        boolean lightNeutralA = "light".equals(toneA) && "neutral".equals(getColorTemperature(normA));
        boolean lightNeutralB = "light".equals(toneB) && "neutral".equals(getColorTemperature(normB));
        return (darkA && lightNeutralB) || (darkB && lightNeutralA);
    }

    private static double fallbackColorScore(String familyA, String familyB) {
        if (familyA == null || familyB == null) return 10.0;
        if (familyA.equals(familyB)) return 30.0;
        if ("neutral".equals(familyA) || "neutral".equals(familyB)) return 25.0;
        if (Set.of("cool", "earth").contains(familyA) && Set.of("cool", "earth").contains(familyB)) return 20.0;
        if (Set.of("warm", "earth").contains(familyA) && Set.of("warm", "earth").contains(familyB)) return 20.0;
        return 10.0;
    }

    /**
     * Boost products matching colorName, colorFamily, or colorTone.
     */
    public static List<ChatProductCard> boostByColorRelevance(List<ChatProductCard> products,
                                                              String requestedColor,
                                                              String requestedFamily,
                                                              boolean requestedDark) {
        if (products == null || products.isEmpty()) {
            return products;
        }

        String reqColorNorm = normalizeColor(requestedColor);
        String reqFamily = requestedFamily != null ? requestedFamily.toLowerCase(Locale.ROOT) : null;

        List<ChatProductCard> sorted = new ArrayList<>(products);
        sorted.sort((p1, p2) -> {
            int score1 = calculateRelevanceScore(p1, reqColorNorm, reqFamily, requestedDark);
            int score2 = calculateRelevanceScore(p2, reqColorNorm, reqFamily, requestedDark);
            return Integer.compare(score2, score1); // descending order
        });
        return sorted;
    }

    private static int calculateRelevanceScore(ChatProductCard product, String reqColorNorm, String reqFamily, boolean requestedDark) {
        int score = 0;
        String prodColorNorm = normalizeColor(product.getColorName());

        // 1. Exact color match (name matching)
        if (reqColorNorm != null && !"unknown".equals(reqColorNorm) && reqColorNorm.equals(prodColorNorm)) {
            score += 100;
        }

        // 2. Color family match
        String prodFamily = product.getColorFamily() != null ? product.getColorFamily().toLowerCase(Locale.ROOT) : null;
        if (reqFamily != null && reqFamily.equals(prodFamily)) {
            score += 30;
        }

        // 3. Derived temperature matching
        String reqTemp = getColorTemperature(reqColorNorm);
        String prodTemp = getColorTemperature(prodColorNorm);
        if (reqTemp != null && !"neutral".equals(reqTemp) && reqTemp.equals(prodTemp)) {
            score += 20;
        }

        // 4. Tone matching
        String prodTone = getColorTone(prodColorNorm);
        if (requestedDark && "dark".equals(prodTone)) {
            score += 15;
        } else if (!requestedDark && "light".equals(prodTone) && reqColorNorm != null && "light".equals(getColorTone(reqColorNorm))) {
            score += 10;
        }

        return score;
    }
}
