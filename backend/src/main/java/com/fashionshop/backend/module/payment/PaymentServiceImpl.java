package com.fashionshop.backend.module.payment;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.Payment;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.PaymentRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.payment.dto.response.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final VnPayService vnPayService;

    // ================================================================
    // 1. Tạo VNPay Payment URL
    // ================================================================

    @Override
    @Transactional
    public String createVnPayPaymentUrl(Long orderId, String ipAddress) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (payment.getMethod() != PaymentMethod.VNPAY) {
            throw new BusinessException(ErrorCode.PAYMENT_CREATION_FAILED, HttpStatus.BAD_REQUEST,
                "Đơn hàng không sử dụng phương thức VNPay");
        }

        // Sinh mã giao dịch duy nhất
        String txnRef = vnPayService.generateTxnRef(orderId);
        payment.setVnpayTxnRef(txnRef);
        paymentRepository.save(payment);

        String paymentUrl = vnPayService.buildPaymentUrl(
            orderId, payment.getAmount(), ipAddress, txnRef);

        log.info("VNPay URL created for order #{} — txnRef={}", orderId, txnRef);
        return paymentUrl;
    }

    // ================================================================
    // 2. IPN Callback — NGUỒN SỰ THẬT DUY NHẤT
    // ================================================================

    @Override
    @Transactional
    public Map<String, String> handleIpn(Map<String, String> params) {
        String secureHash = params.get("vnp_SecureHash");
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String vnpAmount = params.get("vnp_Amount");

        log.info("IPN received — txnRef={}, responseCode={}", txnRef, responseCode);

        // Bước 1: Verify chữ ký
        if (!vnPayService.verifySignature(params, secureHash)) {
            log.warn("IPN invalid signature — txnRef={}", txnRef);
            return ipnResponse("97", "Invalid Signature");
        }

        // Bước 2: Tìm Payment
        Payment payment = paymentRepository.findByVnpayTxnRef(txnRef).orElse(null);
        if (payment == null) {
            log.warn("IPN payment not found — txnRef={}", txnRef);
            return ipnResponse("01", "Order Not Found");
        }

        // Bước 3: Kiểm tra số tiền
        BigDecimal expectedAmount = payment.getAmount().multiply(BigDecimal.valueOf(100));
        if (vnpAmount == null || expectedAmount.compareTo(new BigDecimal(vnpAmount)) != 0) {
            log.warn("IPN invalid amount — expected={}, got={}", expectedAmount, vnpAmount);
            return ipnResponse("04", "Invalid Amount");
        }

        // Bước 4: Idempotent check — đã xử lý rồi thì bỏ qua
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.info("IPN already confirmed — txnRef={}", txnRef);
            return ipnResponse("02", "Already Confirmed");
        }

        // Bước 5: Cập nhật trạng thái
        payment.setVnpayResponseCode(responseCode);
        payment.setVnpayTransactionNo(params.get("vnp_TransactionNo"));
        payment.setVnpayBankCode(params.get("vnp_BankCode"));

        if ("00".equals(responseCode)) {
            // Thanh toán thành công
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());

            // Chuyển order từ AWAITING_PAYMENT → PENDING
            Order order = payment.getOrder();
            order.setPaymentStatus(OrderPaymentStatus.PAID);
            if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                order.setStatus(OrderStatus.PENDING);
                orderRepository.save(order);
                log.info("Order #{} → PENDING (VNPay payment success)", order.getId());
            }
            orderRepository.save(order);
        } else {
            // Thanh toán thất bại
            payment.setStatus(PaymentStatus.FAILED);

            // Hoàn stock + hủy đơn
            Order order = payment.getOrder();
            if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                restoreStockAndCancel(order);
                log.info("Order #{} cancelled — VNPay payment failed (code={})",
                    order.getId(), responseCode);
            }
        }

        paymentRepository.save(payment);
        return ipnResponse("00", "Confirm Success");
    }

    // ================================================================
    // 3. Return URL — redirect + fallback update nếu IPN chưa đến
    // ================================================================

    @Override
    @Transactional
    public String handleReturn(Map<String, String> params) {
        String secureHash = params.get("vnp_SecureHash");
        String responseCode = params.get("vnp_ResponseCode");
        String txnRef = params.get("vnp_TxnRef");

        // Verify signature để chống tamper
        boolean isValid = vnPayService.verifySignature(params, secureHash);

        if (!isValid) {
            log.warn("Return URL invalid signature — txnRef={}", txnRef);
            return "/payment/result?status=failed&reason=invalid_signature";
        }

        // Extract orderId từ txnRef (phần trước timestamp 14 chữ số)
        String orderId = txnRef.length() > 14
            ? txnRef.substring(0, txnRef.length() - 14)
            : txnRef;

        // Fallback: nếu IPN chưa kịp → cập nhật DB từ Return URL
        Payment payment = paymentRepository.findByVnpayTxnRef(txnRef).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setVnpayResponseCode(responseCode);
            payment.setVnpayTransactionNo(params.get("vnp_TransactionNo"));
            payment.setVnpayBankCode(params.get("vnp_BankCode"));

            if ("00".equals(responseCode)) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(LocalDateTime.now());

                Order order = payment.getOrder();
                order.setPaymentStatus(OrderPaymentStatus.PAID);
                if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                    order.setStatus(OrderStatus.PENDING);
                    orderRepository.save(order);
                    log.info("Return fallback: Order #{} → PENDING", order.getId());
                }
                orderRepository.save(order);
            } else {
                payment.setStatus(PaymentStatus.FAILED);

                Order order = payment.getOrder();
                if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                    restoreStockAndCancel(order);
                    log.info("Return fallback: Order #{} cancelled (code={})", order.getId(), responseCode);
                }
            }
            paymentRepository.save(payment);
            log.info("Return fallback: Payment updated — txnRef={}, code={}", txnRef, responseCode);
        }

        String status = "00".equals(responseCode) ? "success" : "failed";
        return "/payment/result?orderId=" + orderId + "&status=" + status;
    }

    // ================================================================
    // 4. Customer: xem payment
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(Long orderId, Long userId) {
        Payment payment = paymentRepository.findByOrderIdAndOrder_UserId(orderId, userId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND));
        return toResponse(payment);
    }

    /**
     * Public endpoint — chỉ trả status string, không cần auth.
     * Dùng cho PaymentResultPage sau redirect VNPay (browser có thể mất JWT).
     */
    @Override
    @Transactional(readOnly = true)
    public String getPaymentStatusByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .map(p -> p.getStatus().name())
            .orElse("NOT_FOUND");
    }

    // ================================================================
    // 5. Admin: danh sách + chi tiết
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> getAllPayments(int page, int size) {
        Page<Payment> payments = paymentRepository.findAllByOrderByCreatedAtDesc(
            PageRequest.of(page, size));
        List<PaymentResponse> content = payments.getContent().stream()
            .map(this::toResponse).toList();
        return PageResponse.from(content, payments);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND));
        return toResponse(payment);
    }

    // ================================================================
    // 6. Admin: Refund (mô phỏng cho ĐATN)
    // ================================================================

    @Override
    @Transactional
    public void processRefund(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND));

        // Chỉ refund khi VNPay + đã PAID
        if (payment.getMethod() != PaymentMethod.VNPAY || payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE, HttpStatus.BAD_REQUEST,
                "Chỉ hoàn tiền cho đơn VNPay đã thanh toán thành công");
        }

        // === Production: gọi VNPay Refund API ở đây ===
        // String response = vnPayService.callRefundApi(payment, reason, ipAddress);
        // if (!"00".equals(response)) throw new BusinessException(REFUND_FAILED, ...);

        // Mô phỏng: cập nhật trạng thái
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());
        payment.setRefundReason(reason);
        payment.getOrder().setPaymentStatus(OrderPaymentStatus.REFUNDED);
        orderRepository.save(payment.getOrder());
        paymentRepository.save(payment);

        log.info("Payment #{} refunded — amount={} — reason={}",
            paymentId, payment.getAmount(), reason);
    }

    // ================================================================
    // Private helpers
    // ================================================================

    /** Hoàn stock + hủy đơn khi VNPay thất bại. */
    private void restoreStockAndCancel(Order order) {
        order.getItems().forEach(item -> {
            if (item.getVariant() != null) {
                variantRepository.increaseStock(item.getVariant().getId(), item.getQuantity());
            }
        });
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason("Thanh toán VNPay thất bại (tự động hủy)");
        orderRepository.save(order);
    }

    private Map<String, String> ipnResponse(String rspCode, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("RspCode", rspCode);
        response.put("Message", message);
        return response;
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
            .id(p.getId())
            .orderId(p.getOrder().getId())
            .method(p.getMethod())
            .status(p.getStatus())
            .amount(p.getAmount())
            .vnpayTxnRef(p.getVnpayTxnRef())
            .vnpayTransactionNo(p.getVnpayTransactionNo())
            .vnpayBankCode(p.getVnpayBankCode())
            .paidAt(p.getPaidAt())
            .refundedAt(p.getRefundedAt())
            .refundReason(p.getRefundReason())
            .createdAt(p.getCreatedAt())
            .build();
    }
}
