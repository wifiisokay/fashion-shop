package com.fashionshop.backend.module.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fashionshop.backend.module.ai.dto.ProductContextDto;
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
    @Builder.Default
    private List<ChatProductCard> products = List.of();
    @Builder.Default
    private List<OutfitComboResponse> outfitCombos = List.of();
    @Builder.Default
    private List<String> styleTips = List.of();
    @Builder.Default
    private List<String> suggestedQuestions = List.of();
    private Integer totalCount;
    private String countType;
    private ProductContextDto productContext;
    private ChatContextDto context;
    private Boolean isFromFallback;
    private String intent;     // nullable — intent đã classify
    private String internalIntent;
    private String searchStatus;
    private LocalDateTime createdAt;
}
