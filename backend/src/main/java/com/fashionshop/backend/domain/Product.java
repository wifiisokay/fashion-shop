package com.fashionshop.backend.domain;

import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.common.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sản phẩm thời trang.
 * styleTags, occasionTags lưu dạng JSON array trong MySQL.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal basePrice;

    @Column(name = "sale_price", precision = 12, scale = 0)
    private BigDecimal salePrice;

    @Column(name = "sale_start_at")
    private LocalDateTime saleStartAt;

    @Column(name = "sale_end_at")
    private LocalDateTime saleEndAt;

    @Column(name = "is_sale", nullable = false)
    @Builder.Default
    private Boolean isSale = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    private String material;

    @Column(name = "estimated_weight", nullable = false)
    @Builder.Default
    private Integer estimatedWeight = 300; // gram

    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 10;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "style_tags", columnDefinition = "json")
    @Builder.Default
    private List<String> styleTags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "occasion_tags", columnDefinition = "json")
    @Builder.Default
    private List<String> occasionTags = new ArrayList<>();

    @Column(name = "fit_type", length = 20)
    private String fitType;

    @Column(length = 20)
    private String season;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductColor> colors = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
