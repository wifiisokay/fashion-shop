package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductImage;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * DTO tóm tắt cho listing (card sản phẩm) — không kèm variant đầy đủ.
 */
@Getter
@Builder
public class ProductSummaryResponse {
    private Long id;
    private String name;
    private BigDecimal basePrice;
    private BigDecimal salePrice;
    private Boolean isSale;
    private String gender;
    private String colorFamily;
    private String primaryImageUrl;
    private String categoryName;
    private String status;

    public static ProductSummaryResponse from(Product product) {
        String primaryImg = product.getImages() != null
            ? product.getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .map(ProductImage::getImageUrl)
                .findFirst()
                .orElse(product.getImages().isEmpty() ? null : product.getImages().get(0).getImageUrl())
            : null;

        return ProductSummaryResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .basePrice(product.getBasePrice())
            .salePrice(product.getSalePrice())
            .isSale(product.getIsSale())
            .gender(product.getGender() != null ? product.getGender().name() : null)
            .colorFamily(product.getColorFamily())
            .primaryImageUrl(primaryImg)
            .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
            .status(product.getStatus().name())
            .build();
    }
}
