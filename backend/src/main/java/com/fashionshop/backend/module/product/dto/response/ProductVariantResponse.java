package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.ProductVariant;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProductVariantResponse {
    private Long id;
    private String color;
    private String size;
    private Integer stockQuantity;
    private BigDecimal priceAdjustment;

    public static ProductVariantResponse from(ProductVariant variant) {
        return ProductVariantResponse.builder()
            .id(variant.getId())
            .color(variant.getColor())
            .size(variant.getSize())
            .stockQuantity(variant.getStockQuantity())
            .priceAdjustment(variant.getPriceAdjustment())
            .build();
    }
}
