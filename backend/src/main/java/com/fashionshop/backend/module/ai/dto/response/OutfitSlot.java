package com.fashionshop.backend.module.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutfitSlot {
    private Long productId;
    private Long colorId;
    private String productName;
    private String colorName;
    private String colorCode;
    private String colorFamily;
    private String slotRole;
    @JsonProperty("isOptional")
    private boolean optional;
    private String imageUrl;
    private String productUrl;
}
