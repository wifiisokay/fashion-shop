package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Màu sắc sản phẩm — tách riêng khỏi variant.
 * Mỗi product có nhiều color, mỗi color có nhiều variant (theo size) và nhiều image.
 * UNIQUE constraint: (product_id, color_name).
 */
@Entity
@Table(name = "product_colors", uniqueConstraints = {
    @UniqueConstraint(name = "uq_product_color", columnNames = {"product_id", "color_name"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "color_name", nullable = false, length = 50)
    private String colorName;

    /** Mã màu hex cho color swatch UI, VD: "#1A1A1A". Nullable. */
    @Column(name = "color_code", length = 7)
    private String colorCode;

    @Column(name = "color_family", length = 20)
    private String colorFamily;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "color", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "color")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();
}
