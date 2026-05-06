package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Đánh giá sản phẩm — gắn với order_item_id (UNIQUE).
 * Chỉ cho phép sửa/xóa trong 7 ngày kể từ createdAt.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Cho phép sửa/xóa trong 7 ngày kể từ khi tạo. */
    public boolean isEditable() {
        return createdAt != null && createdAt.plusDays(7).isAfter(LocalDateTime.now());
    }
}
