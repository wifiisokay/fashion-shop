package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.ProductColor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductColorResponse {
    private Long id;
    private String colorName;
    private String colorCode;
    private Integer displayOrder;

    public static ProductColorResponse from(ProductColor color) {
        return ProductColorResponse.builder()
            .id(color.getId())
            .colorName(color.getColorName())
            .colorCode(color.getColorCode())
            .displayOrder(color.getDisplayOrder())
            .build();
    }
}
