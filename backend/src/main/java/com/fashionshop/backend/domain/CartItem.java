package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Giỏ hàng — mỗi dòng là 1 variant trong giỏ của 1 user.
 * UNIQUE constraint: (user_id, variant_id) — mỗi variant chỉ 1 dòng/giỏ.
 * FK variant_id → ON DELETE RESTRICT: không xóa variant nếu còn trong giỏ.
 */
@Entity
@Table(name = "cart_items", uniqueConstraints = {
    @UniqueConstraint(name = "uq_cart", columnNames = {"user_id", "variant_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false, nullable = false)
    private LocalDateTime addedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
