package com.fashionshop.backend.module.ai.dto;

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
public class ProductContextDto {

    private Long productId;
    private Long colorId;
    private String name;
    private String colorName;
}
