package com.fashionshop.backend.module.order;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.fashionshop.backend.common.enums.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderStatusServiceTest {

    private final OrderStatusService sut = new OrderStatusService();

    @Nested
    @DisplayName("validateTransition")
    class ValidateTransition {

        @Test
        @DisplayName("AWAITING_PAYMENT → PENDING: OK")
        void awaitingToPending() {
            assertDoesNotThrow(() -> sut.validateTransition(AWAITING_PAYMENT, PENDING));
        }

        @Test
        @DisplayName("AWAITING_PAYMENT → CANCELLED: OK")
        void awaitingToCancelled() {
            assertDoesNotThrow(() -> sut.validateTransition(AWAITING_PAYMENT, CANCELLED));
        }

        @Test
        @DisplayName("PENDING → CONFIRMED: OK")
        void pendingToConfirmed() {
            assertDoesNotThrow(() -> sut.validateTransition(PENDING, CONFIRMED));
        }

        @Test
        @DisplayName("CONFIRMED → SHIPPING: OK")
        void confirmedToShipping() {
            assertDoesNotThrow(() -> sut.validateTransition(CONFIRMED, SHIPPING));
        }

        @Test
        @DisplayName("SHIPPING → DELIVERED: OK")
        void shippingToDelivered() {
            assertDoesNotThrow(() -> sut.validateTransition(SHIPPING, DELIVERED));
        }

        @Test
        @DisplayName("DELIVERED → COMPLETED: OK")
        void deliveredToCompleted() {
            assertDoesNotThrow(() -> sut.validateTransition(DELIVERED, COMPLETED));
        }

        @Test
        @DisplayName("PENDING → DELIVERED: INVALID — skip trạng thái")
        void pendingToDelivered_throws() {
            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.validateTransition(PENDING, DELIVERED));
            assertEquals("ORDER_006", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("SHIPPING → PENDING: INVALID — quay ngược")
        void shippingToPending_throws() {
            assertThrows(BusinessException.class,
                () -> sut.validateTransition(SHIPPING, PENDING));
        }

        @Test
        @DisplayName("COMPLETED → bất kỳ: INVALID — trạng thái cuối")
        void completedToAny_throws() {
            assertThrows(BusinessException.class,
                () -> sut.validateTransition(COMPLETED, PENDING));
        }

        @Test
        @DisplayName("CANCELLED → bất kỳ: INVALID — trạng thái cuối")
        void cancelledToAny_throws() {
            assertThrows(BusinessException.class,
                () -> sut.validateTransition(CANCELLED, PENDING));
        }
    }

    @Nested
    @DisplayName("canCustomerCancel")
    class CustomerCancel {

        @Test
        void awaitingPayment_true() {
            assertTrue(sut.canCustomerCancel(AWAITING_PAYMENT));
        }

        @Test
        void pending_true() {
            assertTrue(sut.canCustomerCancel(PENDING));
        }

        @Test
        void confirmed_false() {
            assertFalse(sut.canCustomerCancel(CONFIRMED));
        }

        @Test
        void shipping_false() {
            assertFalse(sut.canCustomerCancel(SHIPPING));
        }
    }

    @Nested
    @DisplayName("canStaffCancel")
    class StaffCancel {

        @Test
        void confirmed_true() {
            assertTrue(sut.canStaffCancel(CONFIRMED));
        }

        @Test
        void shipping_false() {
            assertFalse(sut.canStaffCancel(SHIPPING));
        }

        @Test
        void delivered_false() {
            assertFalse(sut.canStaffCancel(DELIVERED));
        }
    }

    @Nested
    @DisplayName("getLabel")
    class GetLabel {

        @Test
        void pendingLabel() {
            assertEquals("Chờ xác nhận", sut.getLabel(PENDING));
        }

        @Test
        void allStatusesHaveLabels() {
            for (OrderStatus status : OrderStatus.values()) {
                assertNotNull(sut.getLabel(status));
                assertFalse(sut.getLabel(status).isBlank());
            }
        }
    }
}
