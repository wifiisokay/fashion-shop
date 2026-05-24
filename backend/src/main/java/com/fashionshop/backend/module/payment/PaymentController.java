package com.fashionshop.backend.module.payment;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.module.payment.dto.response.PaymentResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Payment Controller — xử lý VNPay callback + Customer xem payment.
 * IPN và Return URL phải là PUBLIC (không cần auth) — VNPay server gọi trực tiếp.
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:https://localhost:5173}")
    private String frontendUrl;

    // ================================================================
    // Public — VNPay IPN Callback (server-to-server)
    // ================================================================

    /**
     * VNPay server gọi endpoint này sau khi khách thanh toán.
     * Đây là NGUỒN SỰ THẬT DUY NHẤT — chỉ IPN mới được update DB.
     * Trả {RspCode, Message} theo format VNPay yêu cầu.
     */
    @GetMapping("/vnpay-ipn")
    public ResponseEntity<Map<String, String>> vnPayIpn(@RequestParam Map<String, String> params) {
        log.info("VNPay IPN received — params={}", params.keySet());
        Map<String, String> result = paymentService.handleIpn(params);
        return ResponseEntity.ok(result);
    }

    // ================================================================
    // Public — VNPay Return URL (redirect browser)
    // ================================================================

    /**
     * VNPay redirect browser của user về đây sau thanh toán.
     * KHÔNG update DB — chỉ verify signature + redirect sang FE.
     */
    @GetMapping("/vnpay-return")
    public void vnPayReturn(@RequestParam Map<String, String> params,
                            HttpServletResponse response) throws Exception {
        String redirectPath = paymentService.handleReturn(params);
        // Redirect về frontend
        response.sendRedirect(frontendUrl + redirectPath);
    }

    // ================================================================
    // Customer — Xem thông tin payment
    // ================================================================

    /**
     * Customer xem payment của đơn mình.
     * Data isolation: chỉ trả payment nếu đơn thuộc user.
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        PaymentResponse payment = paymentService.getPaymentByOrderId(orderId, userId);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    // ================================================================
    // Public — Trạng thái payment (không cần auth, dùng cho PaymentResultPage)
    // ================================================================

    /**
     * Public endpoint — chỉ trả payment status string.
     * Dùng sau khi VNPay redirect browser về FE, lúc đó JWT có thể bị mất.
     * Chỉ trả status (PENDING/SUCCESS/FAILED), không leak thông tin nhạy cảm.
     */
    @GetMapping("/status/{orderId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPaymentStatus(
            @PathVariable Long orderId) {
        String status = paymentService.getPaymentStatusByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", status)));
    }

    // ================================================================
    // Helper
    // ================================================================

    private Long getUserId(org.springframework.security.core.userdetails.UserDetails userDetails) {
        return ((com.fashionshop.backend.domain.User) userDetails).getId();
    }
}
