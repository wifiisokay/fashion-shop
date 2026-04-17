package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ảnh sản phẩm — lưu trên Cloudinary.
 * variantId = NULL → ảnh chung sản phẩm.
 * Chỉ 1 ảnh isPrimary = true trên mỗi product.
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

    /** NULL = ảnh chung sản phẩm, có giá trị = ảnh riêng variant. */
    @Column(name = "variant_id")
    private Long variantId;

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
