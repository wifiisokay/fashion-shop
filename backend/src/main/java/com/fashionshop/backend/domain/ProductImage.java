package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ảnh sản phẩm — lưu trên Cloudinary.
 * color = NULL, isPrimary = TRUE → ảnh chung sản phẩm (listing, chatbot).
 * color != NULL, isPrimary = FALSE → ảnh theo màu (gallery detail).
 * Hai loại row này tách biệt hoàn toàn.
 */
@Entity
@Table(name = "product_images")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** NULL = ảnh chung sản phẩm, có giá trị = ảnh riêng cho màu đó. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id")
    private ProductColor color;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "public_id", nullable = false, length = 300)
    private String publicId;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
