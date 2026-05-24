package com.fashionshop.backend.domain;

import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thanh toán — UNIQUE(order_id): 1 đơn 1 payment.
 * Hỗ trợ COD + VNPay. Lưu đầy đủ thông tin giao dịch VNPay để đối soát.
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
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    /** Mã giao dịch legacy — giữ backward-compat. */
    @Column(name = "transaction_id")
    private String transactionId;

    // ============================================================
    // VNPay fields
    // ============================================================

    /** Mã giao dịch do HỆ THỐNG tạo ra, gửi lên VNPay (orderId + timestamp). */
    @Column(name = "vnpay_txn_ref", unique = true, length = 100)
    private String vnpayTxnRef;

    /** Mã giao dịch VNPay trả về sau thanh toán — dùng để đối soát. */
    @Column(name = "vnpay_transaction_no", length = 100)
    private String vnpayTransactionNo;

    /** Response code VNPay: "00" = thành công. */
    @Column(name = "vnpay_response_code", length = 10)
    private String vnpayResponseCode;

    /** Ngân hàng user dùng để thanh toán qua VNPay. */
    @Column(name = "vnpay_bank_code", length = 20)
    private String vnpayBankCode;

    // ============================================================
    // Timestamps
    // ============================================================

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Thời điểm hoàn tiền (mô phỏng cho ĐATN). */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /** Lý do hoàn tiền. */
    @Column(name = "refund_reason")
    private String refundReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
