package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.ProductImage;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductImageResponse {
    private Long id;
    private String imageUrl;
    private Boolean isPrimary;
    private Integer sortOrder;
    private Long variantId;

    public static ProductImageResponse from(ProductImage image) {
        return ProductImageResponse.builder()
            .id(image.getId())
            .imageUrl(image.getImageUrl())
            .isPrimary(image.getIsPrimary())
            .sortOrder(image.getSortOrder())
            .variantId(image.getVariantId())
            .build();
    }
}
