package com.fashionshop.backend.domain;

import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thanh toán — UNIQUE(order_id): 1 đơn 1 payment.
 * transactionId: mã giao dịch VNPay (nullable cho COD).
 */
@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(name = "uq_payment_order", columnNames = "order_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
