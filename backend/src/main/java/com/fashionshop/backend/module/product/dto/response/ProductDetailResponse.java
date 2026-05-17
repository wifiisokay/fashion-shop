package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.module.storage.CloudinaryUrlBuilder;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * DTO đầy đủ cho trang chi tiết sản phẩm.
 * Kèm colors[] (nested images[] + sizes[]), root images[] chỉ chứa ảnh chung (color=null).
 * Frontend chỉ gọi 1 API, toàn bộ data sẵn sàng.
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
    private String fitType;
    private String season;
    private List<String> styleTags;
    private List<String> occasionTags;
    private String status;
    private CategoryResponse category;
    private List<ProductColorDetailResponse> colors;
    private List<ProductVariantResponse> variants;
    private List<ProductImageResponse> images;

    public static ProductDetailResponse from(Product product) {
        CategoryResponse catResp = product.getCategory() != null
            ? CategoryResponse.from(product.getCategory())
            : null;

        // Nested colors[] với images[] + sizes[] — cho detail page
        List<ProductColorDetailResponse> colorResps = product.getColors() != null
            ? product.getColors().stream().map(ProductColorDetailResponse::from).toList()
            : List.of();

        // Variants flat list — admin dùng
        List<ProductVariantResponse> variantResps = product.getVariants() != null
            ? product.getVariants().stream().map(ProductVariantResponse::from).toList()
            : List.of();

        // Root images: chỉ ảnh chung (color=null), apply detail transform
        List<ProductImageResponse> imageResps = product.getImages() != null
            ? product.getImages().stream()
                .filter(img -> img.getColor() == null && !Boolean.TRUE.equals(img.getIsPrimary()))
                .sorted(Comparator
                    .comparing((com.fashionshop.backend.domain.ProductImage img) -> img.getSortOrder() != null ? img.getSortOrder() : 0)
                    .thenComparing(img -> img.getId() != null ? img.getId() : Long.MAX_VALUE))
                .map(img -> ProductImageResponse.builder()
                    .id(img.getId())
                    .imageUrl(CloudinaryUrlBuilder.detail(img.getImageUrl()))
                    .isPrimary(img.getIsPrimary())
                    .sortOrder(img.getSortOrder())
                    .colorId(null)
                    .build())
                .toList()
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
            .fitType(product.getFitType())
            .season(product.getSeason())
            .styleTags(product.getStyleTags())
            .occasionTags(product.getOccasionTags())
            .status(product.getStatus().name())
            .category(catResp)
            .colors(colorResps)
            .variants(variantResps)
            .images(imageResps)
            .build();
    }
}
