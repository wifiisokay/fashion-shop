package com.fashionshop.backend.module.ai.insight.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO cho endpoint GET /api/products/{productId}/ai-insight.
 *
 * - hasReviewSummary = false khi sản phẩm có ít hơn 3 reviews
 *   hoặc tất cả reviews không có comment (null/blank).
 * - reviewSummary = null khi hasReviewSummary = false.
 * - styleGuide luôn có (dựa vào product data).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiInsightResponse(

        boolean hasReviewSummary,

        ReviewSummary reviewSummary,

        StyleGuide styleGuide,

        LocalDateTime generatedAt

) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewSummary(

            /** Nhận xét tổng quan 1-2 câu. */
            String overall,

            /** Danh sách ưu điểm từ reviews. */
            List<String> pros,

            /** Danh sách nhược điểm từ reviews. */
            List<String> cons,

            /** Nhận xét về phom dáng / size có đúng số không. */
            String fitNote

    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StyleGuide(

            /** Các dịp mặc phù hợp. */
            List<String> occasions,

            /** Gợi ý phối đồ cụ thể. */
            List<String> outfitTips,

            /** Phù hợp với đối tượng / phong cách nào. */
            String whoShouldBuy,

            /** Mùa / thời điểm mặc đẹp nhất. */
            String seasonTip

    ) {}
}
