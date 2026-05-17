package com.fashionshop.backend.module.product.dto.response;

import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.module.storage.CloudinaryUrlBuilder;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Color detail cho trang chi tiết sản phẩm — nested images[] + sizes[].
 * Frontend chỉ gọi 1 API, toàn bộ data đã có sẵn.
 */
@Getter
@Builder
public class ProductColorDetailResponse {
    private Long id;
    private String colorName;
    private String colorCode;
    private Integer displayOrder;
    private Long thumbnailImageId;
    private String thumbnailUrl;
    private List<ColorImageInfo> images;
    private List<ColorSizeInfo> sizes;

    @Getter
    @Builder
    public static class ColorImageInfo {
        private Long id;
        private String imageUrl;
        private Integer sortOrder;
    }

    @Getter
    @Builder
    public static class ColorSizeInfo {
        private Long variantId;
        private String size;
        private Integer stockQuantity;
        private BigDecimal priceAdjustment;
    }

    public static ProductColorDetailResponse from(ProductColor color) {
        var thumbnail = color.getImages() != null
            ? color.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .min(Comparator.comparing(img -> img.getId() != null ? img.getId() : Long.MAX_VALUE))
                .orElse(null)
            : null;

        List<ColorImageInfo> imageInfos = color.getImages() != null
            ? color.getImages().stream()
                .filter(img -> !Boolean.TRUE.equals(img.getIsPrimary()))
                .map(img -> ColorImageInfo.builder()
                    .id(img.getId())
                    .imageUrl(CloudinaryUrlBuilder.detail(img.getImageUrl()))
                    .sortOrder(img.getSortOrder())
                    .build())
                .toList()
            : List.of();

        List<ColorSizeInfo> sizeInfos = color.getVariants() != null
            ? color.getVariants().stream()
                .map(v -> ColorSizeInfo.builder()
                    .variantId(v.getId())
                    .size(v.getSize())
                    .stockQuantity(v.getStockQuantity())
                    .priceAdjustment(v.getPriceAdjustment())
                    .build())
                .toList()
            : List.of();

        return ProductColorDetailResponse.builder()
            .id(color.getId())
            .colorName(color.getColorName())
            .colorCode(color.getColorCode())
            .displayOrder(color.getDisplayOrder())
            .thumbnailImageId(thumbnail != null ? thumbnail.getId() : null)
            .thumbnailUrl(thumbnail != null ? CloudinaryUrlBuilder.detail(thumbnail.getImageUrl()) : null)
            .images(imageInfos)
            .sizes(sizeInfos)
            .build();
    }
}
