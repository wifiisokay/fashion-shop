package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.Product;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO đầy đủ cho trang chi tiết sản phẩm — kèm variants[], images[], tags[].
 */
@Getter
@Builder
public class ProductDetailResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private BigDecimal salePrice;
    private Boolean isSale;
    private String gender;
    private String material;
    private String colorFamily;
    private List<String> styleTags;
    private List<String> occasionTags;
    private String status;
    private CategoryResponse category;
    private List<ProductVariantResponse> variants;
    private List<ProductImageResponse> images;

    public static ProductDetailResponse from(Product product) {
        CategoryResponse catResp = product.getCategory() != null
            ? CategoryResponse.from(product.getCategory())
            : null;

        List<ProductVariantResponse> variantResps = product.getVariants() != null
            ? product.getVariants().stream().map(ProductVariantResponse::from).toList()
            : List.of();

        List<ProductImageResponse> imageResps = product.getImages() != null
            ? product.getImages().stream().map(ProductImageResponse::from).toList()
            : List.of();

        return ProductDetailResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .basePrice(product.getBasePrice())
            .salePrice(product.getSalePrice())
            .isSale(product.getIsSale())
            .gender(product.getGender() != null ? product.getGender().name() : null)
            .material(product.getMaterial())
            .colorFamily(product.getColorFamily())
            .styleTags(product.getStyleTags())
            .occasionTags(product.getOccasionTags())
            .status(product.getStatus().name())
            .category(catResp)
            .variants(variantResps)
            .images(imageResps)
            .build();
    }
}
