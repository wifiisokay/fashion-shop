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
    @DisplayName("PENDING → APPROVED — valid")
    void pendingToApproved() {
        assertDoesNotThrow(() -> sut.validateTransition(ReturnStatus.PENDING, ReturnStatus.APPROVED));
    }

    @Test
    @DisplayName("PENDING → REJECTED — valid")
    void pendingToRejected() {
        assertDoesNotThrow(() -> sut.validateTransition(ReturnStatus.PENDING, ReturnStatus.REJECTED));
    }

    @Test
    @DisplayName("APPROVED → RECEIVED — valid")
    void approvedToReceived() {
        assertDoesNotThrow(() -> sut.validateTransition(ReturnStatus.APPROVED, ReturnStatus.RECEIVED));
    }

    @Test
    @DisplayName("RECEIVED → COMPLETED — valid")
    void receivedToCompleted() {
        assertDoesNotThrow(() -> sut.validateTransition(ReturnStatus.RECEIVED, ReturnStatus.COMPLETED));
    }

    @Test
    @DisplayName("PENDING → COMPLETED — INVALID (skip step)")
    void pendingToCompleted_invalid() {
        assertThrows(BusinessException.class,
            () -> sut.validateTransition(ReturnStatus.PENDING, ReturnStatus.COMPLETED));
    }

    @Test
    @DisplayName("REJECTED → APPROVED — INVALID (terminal state)")
    void rejectedToApproved_invalid() {
        assertThrows(BusinessException.class,
            () -> sut.validateTransition(ReturnStatus.REJECTED, ReturnStatus.APPROVED));
    }

    @Test
    @DisplayName("COMPLETED → PENDING — INVALID (terminal state)")
    void completedToPending_invalid() {
        assertThrows(BusinessException.class,
            () -> sut.validateTransition(ReturnStatus.COMPLETED, ReturnStatus.PENDING));
    }

    // ==================== getLabel ====================

    @Test
    @DisplayName("getLabel — known status")
    void getLabel_known() {
        assertEquals("Chờ xử lý", sut.getLabel(ReturnStatus.PENDING));
        assertEquals("Đã duyệt", sut.getLabel(ReturnStatus.APPROVED));
        assertEquals("Từ chối", sut.getLabel(ReturnStatus.REJECTED));
        assertEquals("Đã nhận hàng", sut.getLabel(ReturnStatus.RECEIVED));
        assertEquals("Hoàn tất", sut.getLabel(ReturnStatus.COMPLETED));
    }
}
