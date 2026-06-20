package com.fashionshop.backend.module.ai.insight;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.module.ai.insight.dto.AiInsightResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Insight endpoint — Public, không yêu cầu đăng nhập.
 * Cùng pattern với ReviewController (public GET /api/products/{productId}/reviews).
 */
@RestController
@RequiredArgsConstructor
public class AiInsightController {

    private final AiInsightService aiInsightService;

    /**
     * GET /api/products/{productId}/ai-insight
     *
     * Trả về AI insight cho trang chi tiết sản phẩm gồm:
     *   - reviewSummary: tóm tắt đánh giá từ khách hàng (null nếu chưa đủ reviews)
     *   - styleGuide: gợi ý phối đồ và dịp mặc
     */
    @GetMapping("/api/products/{productId}/ai-insight")
    public ResponseEntity<ApiResponse<AiInsightResponse>> getInsight(
            @PathVariable Long productId) {
        return ResponseEntity.ok(
                ApiResponse.success(aiInsightService.getInsight(productId)));
    }
}
