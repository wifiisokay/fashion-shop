package com.fashionshop.backend.domain;

import com.fashionshop.backend.common.enums.ReturnStatus;
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
 * Yêu cầu trả hàng — gắn với order_id.
 * Cho phép retry (order_id KHÔNG unique): nếu bị REJECTED, customer tạo mới.
 * evidenceImages: JSON array URL ảnh minh chứng từ Cloudinary.
 */
@Entity
@Table(name = "returns", indexes = {
    @Index(name = "idx_returns_order", columnList = "order_id"),
    @Index(name = "idx_returns_user", columnList = "user_id"),
    @Index(name = "idx_returns_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_images", columnDefinition = "json")
    @Builder.Default
    private List<String> evidenceImages = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReturnStatus status = ReturnStatus.PENDING;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Kiểm tra đơn hàng còn trong cửa sổ trả hàng 7 ngày không.
     * Tính từ order.deliveredAt.
     */
    public boolean isWithinReturnWindow() {
        if (order == null || order.getDeliveredAt() == null) return false;
        return order.getDeliveredAt().plusDays(7).isAfter(LocalDateTime.now());
    }
}
