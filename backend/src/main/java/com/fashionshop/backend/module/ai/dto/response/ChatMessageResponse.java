package com.fashionshop.backend.module.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageResponse {

    private String role;       // "user" | "assistant"
    private String content;    // text content
    private List<ChatProductCard> products;  // nullable — chỉ có khi PRODUCT_SEARCH / OUTFIT_SUGGEST
    private List<String> suggestedQuestions; // nullable — gợi ý câu hỏi tiếp theo
    private String intent;     // nullable — intent đã classify
    private LocalDateTime createdAt;
}
