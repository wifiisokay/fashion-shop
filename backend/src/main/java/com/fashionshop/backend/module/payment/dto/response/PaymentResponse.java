package com.fashionshop.backend.module.payment.dto.response;

import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {

    private Long id;
    private Long orderId;
    private PaymentMethod method;
    private PaymentStatus status;
    private BigDecimal amount;
    private String vnpayTxnRef;
    private String vnpayTransactionNo;
    private String vnpayBankCode;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private String refundReason;
    private LocalDateTime createdAt;
}
