package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Biến thể sản phẩm (color × size).
 * UNIQUE constraint: (color_id, size).
 */
@Entity
@Table(name = "product_variants", uniqueConstraints = {
    @UniqueConstraint(name = "uq_variant_v2", columnNames = {"color_id", "size"})
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id", nullable = false)
    private ProductColor color;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    /** Điều chỉnh giá cho variant này. NULL = dùng basePrice của Product. */
    @Column(name = "price_adjustment", precision = 12, scale = 0)
    private BigDecimal priceAdjustment;
}
