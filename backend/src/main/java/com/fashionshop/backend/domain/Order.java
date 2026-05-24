package com.fashionshop.backend.domain;

import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đơn hàng.
 * addressSnapshot: JSON snapshot địa chỉ tại thời điểm đặt hàng — không FK vào Address.
 * deliveredAt: set khi chuyển SHIPPING → DELIVERED, dùng cho auto-complete job.
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_user", columnList = "user_id"),
    @Index(name = "idx_orders_status_created", columnList = "status, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private OrderPaymentStatus paymentStatus = OrderPaymentStatus.UNPAID;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 0)
    private BigDecimal shippingFee;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal totalAmount;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "address_snapshot", columnDefinition = "json")
    @Builder.Default
    private Map<String, Object> addressSnapshot = new LinkedHashMap<>();

    // ====== Packing / Kích thước kiện hàng ======
    @Column(name = "package_length")
    private Integer packageLength;       // cm

    @Column(name = "package_width")
    private Integer packageWidth;        // cm

    @Column(name = "package_height")
    private Integer packageHeight;       // cm

    @Column(name = "actual_weight")
    private Integer actualWeight;        // gram

    @Column(name = "volumetric_weight")
    private Integer volumetricWeight;    // gram (auto: L×W×H/5000 × 1000)

    @Column(name = "chargeable_weight")
    private Integer chargeableWeight;    // gram = max(actual, volumetric)

    @Column(name = "packing_confirmed")
    @Builder.Default
    private Boolean packingConfirmed = false;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "expected_delivery_date")
    private java.time.LocalDate expectedDeliveryDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private Payment payment;
}
