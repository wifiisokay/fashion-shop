package com.fashionshop.backend.module.ai;

import com.fashionshop.backend.module.ai.dto.response.ChatProductCard;
import com.fashionshop.backend.module.product.ProductTagLibrary;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Local scoring service cho outfit candidates.
 * Chấm điểm sơ bộ trước khi gửi shortlist cho Gemini rerank.
 *
 * Score breakdown (max 100):
 *   colorFamilyScore  0-30 : cùng color family hoặc complementary
 *   fitBalanceScore   0-20 : fitted + loose = ideal
 *   stockScore        0-15 : stock nhiều = ưu tiên
 *   styleTagScore     0-20 : style_tags overlap (placeholder, cần data)
 *   occasionTagScore  0-15 : occasion_tags overlap (placeholder, cần data)
 */
@Service
public class OutfitScoringService {

    private static final Set<String> COMPLEMENTARY_COOL_EARTH = Set.of("cool", "earth");
    private static final Set<String> COMPLEMENTARY_WARM_EARTH = Set.of("warm", "earth");

    /**
     * Tính score tổng hợp cho một candidate so với base product.
     * @param base      sản phẩm gốc
     * @param candidate sản phẩm candidate
     * @return score 0.0 – 100.0
     */
    public double score(ChatProductCard base, ChatProductCard candidate) {
        return scoreWithBreakdown(base, candidate, null, null).total();
    }

    public ScoreBreakdown scoreWithBreakdown(ChatProductCard base, ChatProductCard candidate,
                                             String targetStyle, String targetOccasion) {
        double color = colorFamilyScore(base, candidate);
        double fit = fitBalanceScore(base, candidate);
        double stock = stockScore(candidate);
        double style = styleTagScore(base, candidate, targetStyle);
        double occasion = occasionTagScore(base, candidate, targetOccasion);
        double total = color + fit + stock + style + occasion;
        return new ScoreBreakdown(total, color, fit, stock, style, occasion);
    }

    // =====================
    // Sub-scores
    // =====================

    /**
     * Color family compatibility score (0-30).
     * - Same family (neutral): +30
     * - Neutral + any = good: +25
     * - Complementary pairs (cool+earth, warm+earth): +20
     * - Unknown: +10 (assume OK)
     */
    private double colorFamilyScore(ChatProductCard base, ChatProductCard candidate) {
        String baseFamily = base.getColorFamily();
        String candFamily = candidate.getColorFamily();
        if (baseFamily == null || candFamily == null) return 10;

        if (baseFamily.equals(candFamily)) return 30;
        if ("neutral".equals(baseFamily) || "neutral".equals(candFamily)) return 25;
        if (COMPLEMENTARY_COOL_EARTH.contains(baseFamily) && COMPLEMENTARY_COOL_EARTH.contains(candFamily)) return 20;
        if (COMPLEMENTARY_WARM_EARTH.contains(baseFamily) && COMPLEMENTARY_WARM_EARTH.contains(candFamily)) return 20;
        if ("earth".equals(baseFamily) || "earth".equals(candFamily)) return 18;
        return 8; // other combinations — tránh lạnh+ấm
    }

    /**
     * Fit balance score (0-20).
     * - Fitted + Loose = ideal balance: +20
     * - Neutral với bất kỳ = OK: +12
     * - Cùng nhóm (cả hai fitted hoặc cả hai loose): +0
     */
    private double fitBalanceScore(ChatProductCard base, ChatProductCard candidate) {
        String anchorFit = base.getFitType();
        String candFit = candidate.getFitType();
        if (anchorFit == null || candFit == null) return 8; // unknown → neutral score

        boolean anchorFitted = ProductTagLibrary.FIT_FITTED_GROUP.contains(anchorFit);
        boolean anchorLoose  = ProductTagLibrary.FIT_LOOSE_GROUP.contains(anchorFit);
        boolean anchorNeutral = ProductTagLibrary.FIT_NEUTRAL_GROUP.contains(anchorFit);
        boolean candFitted   = ProductTagLibrary.FIT_FITTED_GROUP.contains(candFit);
        boolean candLoose    = ProductTagLibrary.FIT_LOOSE_GROUP.contains(candFit);
        boolean candNeutral  = ProductTagLibrary.FIT_NEUTRAL_GROUP.contains(candFit);

        if ((anchorLoose && candFitted) || (anchorFitted && candLoose)) return 20;
        if (anchorNeutral || candNeutral) return 12;
        if ((anchorLoose && candLoose) || (anchorFitted && candFitted)) return 0;
        return 8;
    }

    /**
     * Stock availability score (0-15).
     * - Nhiều hàng → ưu tiên (giảm risk out-of-stock)
     */
    private double stockScore(ChatProductCard candidate) {
        Long stock = candidate.getTotalStock();
        if (stock == null) return 5;
        if (stock >= 10) return 15;
        if (stock >= 5) return 10;
        if (stock >= 1) return 5;
        return 0;
    }

    private double styleTagScore(ChatProductCard base, ChatProductCard candidate, String targetStyle) {
        if (candidate.getStyleTags() == null || candidate.getStyleTags().isEmpty()) {
            return 0;
        }
        if (targetStyle != null && candidate.getStyleTags().contains(targetStyle)) {
            return 20;
        }
        if (base != null && base.getStyleTags() != null && !base.getStyleTags().isEmpty()) {
            return overlapScore(base.getStyleTags(), candidate.getStyleTags(), 12);
        }
        return 0;
    }

    private double occasionTagScore(ChatProductCard base, ChatProductCard candidate, String targetOccasion) {
        if (candidate.getOccasionTags() == null || candidate.getOccasionTags().isEmpty()) {
            return 0;
        }
        if (targetOccasion != null && candidate.getOccasionTags().contains(targetOccasion)) {
            return 15;
        }
        if (base != null && base.getOccasionTags() != null && !base.getOccasionTags().isEmpty()) {
            return overlapScore(base.getOccasionTags(), candidate.getOccasionTags(), 8);
        }
        return 0;
    }

    private double overlapScore(java.util.List<String> a, java.util.List<String> b, double maxScore) {
        for (String value : a) {
            if (b.contains(value)) {
                return maxScore;
            }
        }
        return 0;
    }

    public record ScoreBreakdown(double total, double colorFamily, double fitBalance, double stock,
                                  double styleTag, double occasionTag) {
    }
}
