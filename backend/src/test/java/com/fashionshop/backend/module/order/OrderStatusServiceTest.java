package com.fashionshop.backend.module.order;

import static com.fashionshop.backend.common.enums.OrderStatus.AWAITING_PAYMENT;
import static com.fashionshop.backend.common.enums.OrderStatus.CANCELLED;
import static com.fashionshop.backend.common.enums.OrderStatus.COMPLETED;
import static com.fashionshop.backend.common.enums.OrderStatus.CONFIRMED;
import static com.fashionshop.backend.common.enums.OrderStatus.DELIVERED;
import static com.fashionshop.backend.common.enums.OrderStatus.PENDING;
import static com.fashionshop.backend.common.enums.OrderStatus.SHIPPING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.exception.BusinessException;

class OrderStatusServiceTest {

    private final OrderStatusService sut = new OrderStatusService();

    @Nested
    class ValidateTransition {

        @Test
        void awaitingToPending_ok() {
            assertDoesNotThrow(() -> sut.validateTransition(AWAITING_PAYMENT, PENDING));
        }

        @Test
        void awaitingToCancelled_ok() {
            assertDoesNotThrow(() -> sut.validateTransition(AWAITING_PAYMENT, CANCELLED));
        }

        @Test
        void pendingToConfirmed_ok() {
            assertDoesNotThrow(() -> sut.validateTransition(PENDING, CONFIRMED));
        }

        @Test
        void confirmedToShipping_ok() {
            assertDoesNotThrow(() -> sut.validateTransition(CONFIRMED, SHIPPING));
        }

        @Test
        void shippingToCompleted_ok() {
            assertDoesNotThrow(() -> sut.validateTransition(SHIPPING, COMPLETED));
        }

        @Test
        void shippingToDelivered_throws() {
            assertThrows(BusinessException.class, () -> sut.validateTransition(SHIPPING, DELIVERED));
        }

        @Test
        void deliveredToCompleted_throws() {
            assertThrows(BusinessException.class, () -> sut.validateTransition(DELIVERED, COMPLETED));
        }

        @Test
        void pendingToDelivered_throws() {
            assertThrows(BusinessException.class, () -> sut.validateTransition(PENDING, DELIVERED));
        }

        @Test
        void shippingToPending_throws() {
            assertThrows(BusinessException.class, () -> sut.validateTransition(SHIPPING, PENDING));
        }

        @Test
        void completedToAny_throws() {
            assertThrows(BusinessException.class, () -> sut.validateTransition(COMPLETED, PENDING));
        }

        @Test
        void cancelledToAny_throws() {
            assertThrows(BusinessException.class, () -> sut.validateTransition(CANCELLED, PENDING));
        }
    }

    @Nested
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
    class GetLabel {

        @Test
        void allStatusesHaveLabels() {
            for (OrderStatus status : OrderStatus.values()) {
                assertNotNull(sut.getLabel(status));
                assertFalse(sut.getLabel(status).isBlank());
            }
        }
    }
}
