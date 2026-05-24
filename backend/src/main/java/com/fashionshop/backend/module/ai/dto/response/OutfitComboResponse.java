package com.fashionshop.backend.module.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutfitComboResponse {
    private String outfitType;
    private String style;
    private String label;
    private String description;
    private String reason;
    private String colorStory;
    private String occasion;
    private OutfitSlot topSlot;
    private OutfitSlot bottomSlot;
    private OutfitSlot outerSlot;
    private List<ChatProductCard> products;
    private List<ChatProductCard> items;
}
