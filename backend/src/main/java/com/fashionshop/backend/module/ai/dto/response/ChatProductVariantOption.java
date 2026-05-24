package com.fashionshop.backend.module.ai.dto.response;

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
public class ChatProductVariantOption {

    private Long variantId;
    private String sizeName;
    private Integer stockQuantity;
}
