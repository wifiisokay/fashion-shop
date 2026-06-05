package com.fashionshop.backend.module.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutfitSuggestionResponse {
    private String text;
    private Long productId;
    private Long colorId;
    private boolean cached;
    private String provider;          // "GEMINI" hoặc "RULE"
    private LocalDateTime createdAt;
    private List<OutfitComboResponse> combos;
}
