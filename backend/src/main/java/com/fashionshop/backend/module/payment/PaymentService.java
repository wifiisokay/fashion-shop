package com.fashionshop.backend.module.payment;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.module.payment.dto.response.PaymentResponse;

import java.util.Map;

public interface PaymentService {

    /**
     * Tạo VNPay payment URL cho đơn hàng.
     * Gọi sau khi createOrder() với method=VNPAY.
     */
    String createVnPayPaymentUrl(Long orderId, String ipAddress);

    /**
     * Xử lý IPN callback từ VNPay server (nguồn sự thật duy nhất).
     * Trả Map {RspCode, Message} theo format VNPay yêu cầu.
     */
    Map<String, String> handleIpn(Map<String, String> params);

    /**
     * Xử lý Return URL từ VNPay: verify secureHash, fallback cập nhật DB nếu IPN chưa đến,
     * sau đó trả redirect URL cho frontend.
     */
    String handleReturn(Map<String, String> params);

    /** Customer xem payment của đơn mình. */
    PaymentResponse getPaymentByOrderId(Long orderId, Long userId);

    /** Public: chỉ trả status (dùng cho PaymentResultPage sau redirect VNPay). */
    String getPaymentStatusByOrderId(Long orderId);

    /** Admin: danh sách tất cả giao dịch. */
    PageResponse<PaymentResponse> getAllPayments(int page, int size);

    /** Admin: chi tiết 1 payment. */
    PaymentResponse getPaymentById(Long paymentId);

    /** Admin: hoàn tiền (mô phỏng cho ĐATN). */
    void processRefund(Long paymentId, String reason);
}
