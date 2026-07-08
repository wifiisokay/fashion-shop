package com.fashionshop.backend.module.ai.insight;

import com.fashionshop.backend.module.ai.insight.dto.AiInsightResponse;

public interface AiInsightService {

    /**
     * Trả về AI insight cho sản phẩm: tóm tắt reviews + style guide.
     * Kết quả được cache Redis 12 giờ.
     * Nếu Gemini lỗi: trả về fallback từ product data (không throw exception).
     */
    AiInsightResponse getInsight(Long productId);
}
