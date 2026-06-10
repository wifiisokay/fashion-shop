package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReturnStatusServiceTest {

    private final ReturnStatusService sut = new ReturnStatusService();

    // ==================== validateTransition ====================

    @Test
    @DisplayName("REQUESTED → APPROVED — valid")
    void requestedToApproved() {
        assertDoesNotThrow(() -> sut.validateTransition(ReturnStatus.REQUESTED, ReturnStatus.APPROVED));
    }

    @Test
    @DisplayName("REQUESTED → REJECTED — valid")
    void requestedToRejected() {
        assertDoesNotThrow(() -> sut.validateTransition(ReturnStatus.REQUESTED, ReturnStatus.REJECTED));
    }

    @Test
    @DisplayName("APPROVED → RECEIVED — valid")
    void approvedToReceived() {
        assertDoesNotThrow(() -> sut.validateTransition(ReturnStatus.APPROVED, ReturnStatus.RECEIVED));
    }

    @Test
    @DisplayName("RECEIVED → COMPLETED/REFUNDED — valid")
    void receivedToCompleted() {
        assertDoesNotThrow(() -> sut.validateTransition(ReturnStatus.RECEIVED, ReturnStatus.COMPLETED));
        assertDoesNotThrow(() -> sut.validateTransition(ReturnStatus.RECEIVED, ReturnStatus.REFUNDED));
    }

    @Test
    @DisplayName("REQUESTED → COMPLETED/REFUNDED — INVALID (skip step)")
    void requestedToCompleted_invalid() {
        assertThrows(BusinessException.class,
            () -> sut.validateTransition(ReturnStatus.REQUESTED, ReturnStatus.COMPLETED));
        assertThrows(BusinessException.class,
            () -> sut.validateTransition(ReturnStatus.REQUESTED, ReturnStatus.REFUNDED));
    }

    @Test
    @DisplayName("REJECTED → APPROVED — INVALID (terminal state)")
    void rejectedToApproved_invalid() {
        assertThrows(BusinessException.class,
            () -> sut.validateTransition(ReturnStatus.REJECTED, ReturnStatus.APPROVED));
    }

    @Test
    @DisplayName("COMPLETED/REFUNDED → REQUESTED — INVALID (terminal state)")
    void completedToRequested_invalid() {
        assertThrows(BusinessException.class,
            () -> sut.validateTransition(ReturnStatus.COMPLETED, ReturnStatus.REQUESTED));
        assertThrows(BusinessException.class,
            () -> sut.validateTransition(ReturnStatus.REFUNDED, ReturnStatus.REQUESTED));
    }

    // ==================== getLabel ====================

    @Test
    @DisplayName("getLabel — known status")
    void getLabel_known() {
        assertEquals("Chờ duyệt", sut.getLabel(ReturnStatus.REQUESTED));
        assertEquals("Đã duyệt", sut.getLabel(ReturnStatus.APPROVED));
        assertEquals("Đã từ chối", sut.getLabel(ReturnStatus.REJECTED));
        assertEquals("Đã nhận hàng trả", sut.getLabel(ReturnStatus.RECEIVED));
        assertEquals("Đã hoàn tiền", sut.getLabel(ReturnStatus.REFUNDED));
        assertEquals("Đã hoàn tiền", sut.getLabel(ReturnStatus.COMPLETED));
    }
}
