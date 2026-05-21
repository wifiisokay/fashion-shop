package com.fashionshop.backend.module.product.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Response chứa tag được Gemini gợi ý + các arrays của toàn bộ tag library.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductTagSuggestResponse {

    /** Tags được AI gợi ý — admin có thể tick/bỏ tick tự do */
    private List<String> styleTags;
    private List<String> occasionTags;
    private String fitType;
    private String colorFamily;

    /**
     * Độ tin cậy của gợi ý:
     * HIGH  — description >= 50 chars
     * MEDIUM — description >= 20 chars
     */
    private String confidence;

    /** Message giải thích nếu AI không thể gợi ý */
    private String message;
}
