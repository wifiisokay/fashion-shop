package com.fashionshop.backend.module.payment;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.module.payment.dto.request.RefundRequest;
import com.fashionshop.backend.module.payment.dto.response.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin Payment Controller — quản lý giao dịch thanh toán.
 * Chỉ ADMIN mới có quyền xem tất cả + kích hoạt refund.
 */
@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;

    /** Danh sách tất cả giao dịch (phân trang). */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
            paymentService.getAllPayments(page, size)));
    }

    /** Chi tiết 1 payment record. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
            paymentService.getPaymentById(id)));
    }

    /** Kích hoạt hoàn tiền thủ công (mô phỏng cho ĐATN). */
    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<Void>> refundPayment(
            @PathVariable Long id,
            @Valid @RequestBody RefundRequest request) {
        paymentService.processRefund(id, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Hoàn tiền thành công", null));
    }
}
