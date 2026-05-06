package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductImage;
import com.fashionshop.backend.module.storage.CloudinaryUrlBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO tóm tắt cho listing (card sản phẩm) — không kèm variant đầy đủ.
 * primaryImageUrl lấy từ ảnh có color=NULL + isPrimary=true, apply listing transform.
 */
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
        // Ảnh chung: color IS NULL + isPrimary=true → dùng cho listing
        String primaryImg = product.getImages() != null
            ? product.getImages().stream()
                .filter(img -> img.getColor() == null && Boolean.TRUE.equals(img.getIsPrimary()))
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
            .fitType(product.getFitType())
            .season(product.getSeason())
            .primaryImageUrl(CloudinaryUrlBuilder.listing(primaryImg))
            .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
            .status(product.getStatus().name())
            .build();
    }
}
