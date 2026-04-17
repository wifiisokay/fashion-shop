package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Biến thể sản phẩm (color + size).
 * UNIQUE constraint: (product_id, color, size).
 */
@Entity
@Table(name = "product_variants", uniqueConstraints = {
    @UniqueConstraint(name = "uk_variant_product_color_size",
                      columnNames = {"product_id", "color", "size"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String color;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    /** Điều chỉnh giá cho variant này. NULL = dùng basePrice của Product. */
    @Column(name = "price_adjustment", precision = 12, scale = 0)
    private BigDecimal priceAdjustment;
}
