package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductImage;
import com.fashionshop.backend.module.storage.CloudinaryUrlBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Comparator;

@Getter
@Setter
@Builder
public class ProductSummaryResponse {
    private Long id;
    private String name;
    private BigDecimal basePrice;
    private BigDecimal salePrice;
    private Boolean isSale;
    private String gender;
    private String colorFamily;
    private String fitType;
    private String season;
    private String primaryImageUrl;
    private String categoryName;
    private String status;
    private Double avgRating;
    private Integer reviewCount;

    public static ProductSummaryResponse from(Product product) {
        String primaryImg = product.getImages() != null
            ? product.getImages().stream()
                .filter(img -> img.getColor() != null && Boolean.TRUE.equals(img.getIsPrimary()))
                .min(Comparator
                    .comparing((ProductImage img) -> img.getColor().getDisplayOrder() != null ? img.getColor().getDisplayOrder() : 0)
                    .thenComparing(img -> img.getColor().getId() != null ? img.getColor().getId() : Long.MAX_VALUE)
                    .thenComparing(img -> img.getId() != null ? img.getId() : Long.MAX_VALUE))
                .map(ProductImage::getImageUrl)
                .orElseGet(() -> product.getImages().stream()
                    .filter(img -> img.getColor() == null && !Boolean.TRUE.equals(img.getIsPrimary()))
                    .min(Comparator
                        .comparing((ProductImage img) -> img.getSortOrder() != null ? img.getSortOrder() : 0)
                        .thenComparing(img -> img.getId() != null ? img.getId() : Long.MAX_VALUE))
                    .map(ProductImage::getImageUrl)
                    .orElse(null))
            : null;

        return ProductSummaryResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .basePrice(product.getBasePrice())
            .salePrice(product.getSalePrice())
            .isSale(product.getIsSale())
            .gender(product.getGender() != null ? product.getGender().name() : null)
            .colorFamily(product.getColorFamily())
            .fitType(product.getFitType())
            .season(product.getSeason())
            .primaryImageUrl(CloudinaryUrlBuilder.listing(primaryImg))
            .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
            .status(product.getStatus().name())
            .build();
    }
}
