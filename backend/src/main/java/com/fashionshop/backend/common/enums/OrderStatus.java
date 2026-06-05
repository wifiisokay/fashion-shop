package com.fashionshop.backend.common.enums;

/**
 * Order lifecycle:
 * AWAITING_PAYMENT → (VNPay paid / COD) → PENDING
 * PENDING -> CONFIRMED -> SHIPPING -> COMPLETED
 * Any non-COMPLETED → CANCELLED
 */
public enum OrderStatus {
    AWAITING_PAYMENT,
    PENDING,
    CONFIRMED,
    SHIPPING,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURNING,
    RETURNED
}
