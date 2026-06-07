package com.fashionshop.backend.module.payment;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.Payment;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.PaymentRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.module.payment.dto.response.PaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock VnPayService vnPayService;

    @InjectMocks PaymentServiceImpl sut;

    // ==================== Helpers ====================

    private Payment mockPayment(PaymentMethod method, PaymentStatus status) {
        Order order = Order.builder()
            .id(1L)
            .user(User.builder().id(100L).build())
            .status(OrderStatus.AWAITING_PAYMENT)
            .paymentMethod(method)
            .totalAmount(BigDecimal.valueOf(350000))
            .build();

        return Payment.builder()
            .id(10L)
            .order(order)
            .method(method)
            .status(status)
            .amount(BigDecimal.valueOf(350000))
            .vnpayTxnRef("12320260429143022")
            .build();
    }

    private Map<String, String> mockIpnParams(String responseCode, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "12320260429143022");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_Amount", amount);
        params.put("vnp_TransactionNo", "VNP123456");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_SecureHash", "valid_hash");
        return params;
    }

    // ==================== createVnPayPaymentUrl ====================

    @Nested
    @DisplayName("createVnPayPaymentUrl")
    class CreateVnPayUrl {

        @Test
        @DisplayName("Tạo URL thành công cho đơn VNPay")
        void success() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.PENDING);
            when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
            when(vnPayService.generateTxnRef(1L)).thenReturn("12320260429143022");
            when(vnPayService.buildPaymentUrl(eq(1L), any(), eq("127.0.0.1"), eq("12320260429143022")))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?params");
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String url = sut.createVnPayPaymentUrl(1L, "127.0.0.1");

            assertNotNull(url);
            assertTrue(url.contains("sandbox.vnpayment.vn"));
            assertEquals("12320260429143022", payment.getVnpayTxnRef());
            verify(paymentRepository).save(payment);
        }

        @Test
        @DisplayName("Payment không tồn tại — throw PAYMENT_NOT_FOUND")
        void notFound_throws() {
            when(paymentRepository.findByOrderIdForUpdate(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createVnPayPaymentUrl(99L, "127.0.0.1"));
            assertEquals("PAYMENT_003", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Đơn COD — throw PAYMENT_CREATION_FAILED")
        void codMethod_throws() {
            Payment payment = mockPayment(PaymentMethod.COD, PaymentStatus.PENDING);
            when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createVnPayPaymentUrl(1L, "127.0.0.1"));
            assertEquals("PAYMENT_009", ex.getErrorCode().getCode());
        }
    }

    // ==================== handleIpn ====================

    @Nested
    @DisplayName("handleIpn — IPN Callback")
    class HandleIpn {

        @Test
        @DisplayName("Thanh toán thành công — payment SUCCESS + order PENDING")
        void success_updatesPaymentAndOrder() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.PENDING);
            Order order = payment.getOrder();
            Map<String, String> params = mockIpnParams("00", "35000000"); // 350000 * 100

            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(true);
            when(paymentRepository.findByVnpayTxnRefForUpdate("12320260429143022")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> result = sut.handleIpn(params);

            assertEquals("00", result.get("RspCode"));
            assertEquals("Confirm Success", result.get("Message"));
            assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
            assertNotNull(payment.getPaidAt());
            assertEquals("VNP123456", payment.getVnpayTransactionNo());
            assertEquals("NCB", payment.getVnpayBankCode());
            assertEquals(OrderStatus.PENDING, order.getStatus());
            assertEquals(OrderPaymentStatus.PAID, order.getPaymentStatus());
        }

        @Test
        @DisplayName("Thanh toán thất bại — payment FAILED + hoàn stock + hủy đơn")
        void failed_cancelsOrderAndRestoresStock() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.PENDING);
            Order order = payment.getOrder();
            order.setItems(new java.util.ArrayList<>());
            Map<String, String> params = mockIpnParams("24", "35000000"); // code 24 = user cancel

            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(true);
            when(paymentRepository.findByVnpayTxnRefForUpdate("12320260429143022")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> result = sut.handleIpn(params);

            assertEquals("00", result.get("RspCode"));
            assertEquals(PaymentStatus.FAILED, payment.getStatus());
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
            assertEquals(OrderPaymentStatus.UNPAID, order.getPaymentStatus());
        }

        @Test
        @DisplayName("Chữ ký không hợp lệ — trả RspCode 97")
        void invalidSignature_returns97() {
            Map<String, String> params = mockIpnParams("00", "35000000");
            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(false);

            Map<String, String> result = sut.handleIpn(params);

            assertEquals("97", result.get("RspCode"));
            assertEquals("Invalid Signature", result.get("Message"));
            verifyNoInteractions(paymentRepository);
        }

        @Test
        @DisplayName("Không tìm thấy payment theo txnRef — trả RspCode 01")
        void paymentNotFound_returns01() {
            Map<String, String> params = mockIpnParams("00", "35000000");
            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(true);
            when(paymentRepository.findByVnpayTxnRefForUpdate("12320260429143022")).thenReturn(Optional.empty());

            Map<String, String> result = sut.handleIpn(params);

            assertEquals("01", result.get("RspCode"));
            assertEquals("Order Not Found", result.get("Message"));
        }

        @Test
        @DisplayName("Số tiền không khớp — trả RspCode 04")
        void invalidAmount_returns04() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.PENDING);
            Map<String, String> params = mockIpnParams("00", "99999999"); // sai số tiền

            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(true);
            when(paymentRepository.findByVnpayTxnRefForUpdate("12320260429143022")).thenReturn(Optional.of(payment));

            Map<String, String> result = sut.handleIpn(params);

            assertEquals("04", result.get("RspCode"));
            assertEquals("Invalid Amount", result.get("Message"));
        }

        @Test
        @DisplayName("Idempotent — IPN gọi 2 lần cùng txnRef → trả RspCode 02")
        void alreadyConfirmed_returns02() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.SUCCESS); // đã SUCCESS
            Map<String, String> params = mockIpnParams("00", "35000000");

            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(true);
            when(paymentRepository.findByVnpayTxnRefForUpdate("12320260429143022")).thenReturn(Optional.of(payment));

            Map<String, String> result = sut.handleIpn(params);

            assertEquals("02", result.get("RspCode"));
            assertEquals("Already Confirmed", result.get("Message"));
            // Không gọi save — không update lần 2
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order đã CANCELLED — IPN success muộn không hồi sinh order")
        void cancelledOrder_successIpnDoesNotReviveOrder() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.PENDING);
            payment.getOrder().setStatus(OrderStatus.CANCELLED);
            Map<String, String> params = mockIpnParams("00", "35000000");

            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(true);
            when(paymentRepository.findByVnpayTxnRefForUpdate("12320260429143022")).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> result = sut.handleIpn(params);

            assertEquals("02", result.get("RspCode"));
            assertEquals("Already Confirmed", result.get("Message"));
            assertEquals(PaymentStatus.FAILED, payment.getStatus());
            assertEquals(OrderStatus.CANCELLED, payment.getOrder().getStatus());
            assertNotEquals(OrderPaymentStatus.PAID, payment.getOrder().getPaymentStatus());
            verify(orderRepository, never()).save(any());
        }
    }

    // ==================== handleReturn ====================

    @Nested
    @DisplayName("handleReturn — Return URL redirect only")
    class HandleReturn {

        @Test
        @DisplayName("Success → redirect frontend, không update DB")
        void success_redirectOnly() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.PENDING);
            Order order = payment.getOrder();
            Map<String, String> params = mockIpnParams("00", "35000000");

            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(true);

            String redirect = sut.handleReturn(params);

            assertTrue(redirect.contains("status=success"));
            assertTrue(redirect.contains("orderId=1"));
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
            assertNull(payment.getPaidAt());
            assertEquals(OrderStatus.AWAITING_PAYMENT, order.getStatus());
            assertEquals(OrderPaymentStatus.UNPAID, order.getPaymentStatus());
            verifyNoInteractions(paymentRepository, orderRepository);
        }

        @Test
        @DisplayName("Failed → redirect frontend, không update DB")
        void failed_redirectOnly() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.PENDING);
            Order order = payment.getOrder();
            order.setItems(new java.util.ArrayList<>());
            Map<String, String> params = mockIpnParams("24", "35000000");

            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(true);

            String redirect = sut.handleReturn(params);

            assertTrue(redirect.contains("status=failed"));
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
            assertEquals(OrderStatus.AWAITING_PAYMENT, order.getStatus());
            assertEquals(OrderPaymentStatus.UNPAID, order.getPaymentStatus());
            verifyNoInteractions(paymentRepository, orderRepository);
        }

        @Test
        @DisplayName("IPN đã xử lý trước → return URL vẫn chỉ redirect")
        void alreadyProcessed_redirectOnly() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.SUCCESS);
            Map<String, String> params = mockIpnParams("00", "35000000");

            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(true);

            String redirect = sut.handleReturn(params);

            assertTrue(redirect.contains("status=success"));
            assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Invalid signature — redirect error, không update DB")
        void invalidSignature_redirectsWithError() {
            Map<String, String> params = mockIpnParams("00", "35000000");
            when(vnPayService.verifySignature(eq(params), eq("valid_hash"))).thenReturn(false);

            String redirect = sut.handleReturn(params);

            assertTrue(redirect.contains("invalid_signature"));
            verifyNoInteractions(paymentRepository);
        }
    }

    // ==================== getPaymentByOrderId ====================

    @Nested
    @DisplayName("getPaymentByOrderId — Customer")
    class GetPaymentByOrderId {

        @Test
        @DisplayName("Customer xem payment của đơn mình — OK")
        void success() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.SUCCESS);
            when(paymentRepository.findByOrderIdAndOrder_UserId(1L, 100L))
                .thenReturn(Optional.of(payment));

            PaymentResponse response = sut.getPaymentByOrderId(1L, 100L);

            assertEquals(10L, response.getId());
            assertEquals("VNPAY", response.getMethod().name());
            assertEquals("SUCCESS", response.getStatus().name());
        }

        @Test
        @DisplayName("Đơn không thuộc user — throw PAYMENT_NOT_FOUND")
        void notOwner_throws() {
            when(paymentRepository.findByOrderIdAndOrder_UserId(1L, 999L))
                .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.getPaymentByOrderId(1L, 999L));
            assertEquals("PAYMENT_003", ex.getErrorCode().getCode());
        }
    }

    // ==================== processRefund ====================

    @Nested
    @DisplayName("processRefund — Admin Refund")
    class ProcessRefund {

        @Test
        @DisplayName("VNPay SUCCESS → REFUNDED thành công")
        void success() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.SUCCESS);
            when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            sut.processRefund(10L, "Giao hàng thất bại");

            assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
            assertEquals(OrderPaymentStatus.REFUNDED, payment.getOrder().getPaymentStatus());
            assertNotNull(payment.getRefundedAt());
            assertEquals("Giao hàng thất bại", payment.getRefundReason());
            verify(paymentRepository).save(payment);
        }

        @Test
        @DisplayName("COD — throw REFUND_NOT_ELIGIBLE")
        void codMethod_throws() {
            Payment payment = mockPayment(PaymentMethod.COD, PaymentStatus.SUCCESS);
            when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.processRefund(10L, "test"));
            assertEquals("PAYMENT_008", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("VNPay PENDING (chưa thanh toán) — throw REFUND_NOT_ELIGIBLE")
        void pending_throws() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.PENDING);
            when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.processRefund(10L, "test"));
            assertEquals("PAYMENT_008", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Payment không tồn tại — throw PAYMENT_NOT_FOUND")
        void notFound_throws() {
            when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.processRefund(99L, "test"));
            assertEquals("PAYMENT_003", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("VNPay FAILED — throw REFUND_NOT_ELIGIBLE")
        void failed_throws() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.FAILED);
            when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.processRefund(10L, "test"));
            assertEquals("PAYMENT_008", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Đã REFUNDED (double refund) — throw REFUND_NOT_ELIGIBLE")
        void alreadyRefunded_throws() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.REFUNDED);
            when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.processRefund(10L, "test"));
            assertEquals("PAYMENT_008", ex.getErrorCode().getCode());
            // Không gọi save — chặn double refund
            verify(paymentRepository, never()).save(any());
        }
    }

    // ==================== getPaymentStatusByOrderId ====================

    @Nested
    @DisplayName("getPaymentStatusByOrderId — Public")
    class GetPaymentStatusByOrderId {

        @Test
        @DisplayName("Payment SUCCESS → trả 'SUCCESS'")
        void found_returnsStatus() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.SUCCESS);
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

            assertEquals("SUCCESS", sut.getPaymentStatusByOrderId(1L));
        }

        @Test
        @DisplayName("Payment PENDING → trả 'PENDING'")
        void pending_returnsPending() {
            Payment payment = mockPayment(PaymentMethod.VNPAY, PaymentStatus.PENDING);
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

            assertEquals("PENDING", sut.getPaymentStatusByOrderId(1L));
        }

        @Test
        @DisplayName("Không tìm thấy → trả 'NOT_FOUND'")
        void notFound_returnsNotFound() {
            when(paymentRepository.findByOrderId(99L)).thenReturn(Optional.empty());

            assertEquals("NOT_FOUND", sut.getPaymentStatusByOrderId(99L));
        }
    }
}
