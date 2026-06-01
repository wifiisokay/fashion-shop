package com.fashionshop.backend.common.enums;

/**
 * Return lifecycle:
 * REQUESTED → APPROVED → RECEIVED → COMPLETED
 *             ↘ REJECTED
 */
public enum ReturnStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    RECEIVED,
    COMPLETED
}
