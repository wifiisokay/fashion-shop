package com.fashionshop.backend.common.enums;

/**
 * Return lifecycle:
 * PENDING → APPROVED → RECEIVED → COMPLETED
 *           ↘ REJECTED
 */
public enum ReturnStatus {
    PENDING,
    APPROVED,
    REJECTED,
    RECEIVED,
    COMPLETED
}
