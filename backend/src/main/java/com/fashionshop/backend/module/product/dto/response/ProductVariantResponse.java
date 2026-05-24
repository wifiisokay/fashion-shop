package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.ProductVariant;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProductVariantResponse {
    private Long id;
    private Long colorId;
    private String colorName;
    private String size;
    private Integer stockQuantity;
    private BigDecimal priceAdjustment;

    public static ProductVariantResponse from(ProductVariant variant) {
        return ProductVariantResponse.builder()
            .id(variant.getId())
            .colorId(variant.getColor() != null ? variant.getColor().getId() : null)
            .colorName(variant.getColor() != null ? variant.getColor().getColorName() : null)
            .size(variant.getSize())
            .stockQuantity(variant.getStockQuantity())
            .priceAdjustment(variant.getPriceAdjustment())
            .build();
    }
}
