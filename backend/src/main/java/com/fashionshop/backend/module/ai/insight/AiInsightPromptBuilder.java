package com.fashionshop.backend.module.ai.insight;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.Review;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Build prompt gửi Gemini cho tính năng AI Insight.
 *
 * Yêu cầu Gemini trả về JSON thuần (không markdown, không backtick).
 * Nếu không có đủ reviews, phần reviewSummary bị bỏ khỏi prompt
 * và Gemini chỉ cần điền styleGuide.
 */
@Component
public class AiInsightPromptBuilder {

    private static final int MIN_REVIEWS_FOR_SUMMARY = 3;

    /**
     * @param product    thông tin sản phẩm
     * @param reviews    danh sách reviews (có thể rỗng)
     * @param avgRating  rating trung bình (0.0 nếu không có reviews)
     * @return prompt string gửi cho Gemini
     */
    public String build(Product product, List<Review> reviews, double avgRating) {
        List<Review> withComment = reviews.stream()
                .filter(r -> r.getComment() != null && !r.getComment().isBlank())
                .collect(Collectors.toList());

        boolean hasEnoughReviews = withComment.size() >= MIN_REVIEWS_FOR_SUMMARY;

        StringBuilder sb = new StringBuilder();

        sb.append("""
                Bạn là trợ lý thời trang cho shop Fashi (Việt Nam).
                Nhiệm vụ: phân tích thông tin sản phẩm""");

        if (hasEnoughReviews) {
            sb.append(" và đánh giá từ khách hàng");
        }

        sb.append("""
                , trả về JSON hợp lệ duy nhất.
                Quy tắc bắt buộc:
                - Chỉ trả về JSON, KHÔNG có markdown, KHÔNG có backtick, KHÔNG có giải thích.
                - Tất cả nội dung bằng tiếng Việt, ngắn gọn, thực tế.
                - Mỗi item trong mảng tối đa 15 từ.
                """);

        // ── Cấu trúc JSON yêu cầu ──
        if (hasEnoughReviews) {
            sb.append("""
                    Cấu trúc JSON trả về:
                    {
                      "reviewSummary": {
                        "overall": "Nhận xét tổng quan 1-2 câu về sản phẩm dựa trên reviews",
                        "pros": ["ưu điểm 1", "ưu điểm 2", "ưu điểm 3"],
                        "cons": ["nhược điểm 1", "nhược điểm 2"],
                        "fitNote": "Nhận xét về phom dáng và size có đúng số không"
                      },
                      "styleGuide": {
                        "occasions": ["dịp mặc 1", "dịp mặc 2", "dịp mặc 3"],
                        "outfitTips": ["gợi ý phối đồ 1", "gợi ý phối đồ 2", "gợi ý phối đồ 3"],
                        "whoShouldBuy": "Phù hợp với đối tượng và phong cách nào",
                        "seasonTip": "Mùa hoặc thời điểm mặc đẹp nhất"
                      }
                    }
                    """);
        } else {
            sb.append("""
                    Cấu trúc JSON trả về (chỉ có styleGuide vì chưa đủ reviews):
                    {
                      "styleGuide": {
                        "occasions": ["dịp mặc 1", "dịp mặc 2", "dịp mặc 3"],
                        "outfitTips": ["gợi ý phối đồ 1", "gợi ý phối đồ 2", "gợi ý phối đồ 3"],
                        "whoShouldBuy": "Phù hợp với đối tượng và phong cách nào",
                        "seasonTip": "Mùa hoặc thời điểm mặc đẹp nhất"
                      }
                    }
                    """);
        }

        // ── Thông tin sản phẩm ──
        sb.append("\n=== THÔNG TIN SẢN PHẨM ===\n");
        sb.append("Tên: ").append(nullSafe(product.getName())).append("\n");
        sb.append("Chất liệu: ").append(nullSafe(product.getMaterial())).append("\n");
        sb.append("Phom dáng: ").append(nullSafe(product.getFitType())).append("\n");
        sb.append("Mùa: ").append(nullSafe(product.getSeason())).append("\n");

        if (product.getStyleTags() != null && !product.getStyleTags().isEmpty()) {
            sb.append("Style tags: ").append(String.join(", ", product.getStyleTags())).append("\n");
        }
        if (product.getOccasionTags() != null && !product.getOccasionTags().isEmpty()) {
            sb.append("Occasion tags: ").append(String.join(", ", product.getOccasionTags())).append("\n");
        }
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            String desc = product.getDescription().length() > 300
                    ? product.getDescription().substring(0, 300) + "..."
                    : product.getDescription();
            sb.append("Mô tả: ").append(desc).append("\n");
        }

        // ── Dữ liệu reviews (chỉ khi đủ) ──
        if (hasEnoughReviews) {
            sb.append("\n=== ĐÁNH GIÁ KHÁCH HÀNG ===\n");
            sb.append("Tổng ").append(withComment.size()).append(" đánh giá có nội dung");
            if (avgRating > 0) {
                sb.append(String.format(", rating trung bình %.1f/5", avgRating));
            }
            sb.append(":\n");

            // Lấy tối đa 30 comments để tránh vượt context window
            withComment.stream()
                    .limit(30)
                    .forEach(r -> sb.append(String.format("- (%d★) %s\n",
                            r.getRating(),
                            truncate(r.getComment(), 150))));
        }

        return sb.toString();
    }

    private String nullSafe(String value) {
        return value != null ? value : "Không có thông tin";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
