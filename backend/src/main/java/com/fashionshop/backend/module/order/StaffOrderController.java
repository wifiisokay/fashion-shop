package com.fashionshop.backend.module.order;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.module.order.dto.request.CancelOrderRequest;
import com.fashionshop.backend.module.order.dto.request.ConfirmPackingRequest;
import com.fashionshop.backend.module.order.dto.request.UpdateOrderStatusRequest;
import com.fashionshop.backend.module.order.dto.response.OrderDetailResponse;
import com.fashionshop.backend.module.order.dto.response.OrderSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
@Tag(name = "Orders — Staff/Admin", description = "Quản lý đơn hàng cho nhân viên và admin")
public class StaffOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Danh sách tất cả đơn hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> list(
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            orderService.getAllOrders(status, keyword, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết đơn hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái đơn hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<OrderDetailResponse>> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật trạng thái thành công", orderService.updateOrderStatus(id, request)));
    }

    @PatchMapping("/{id}/packing")
    @Operation(summary = "Xác nhận đóng gói kiện hàng (bắt buộc trước khi giao)",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<OrderDetailResponse>> confirmPacking(
        @PathVariable Long id,
        @Valid @RequestBody ConfirmPackingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Xác nhận đóng gói thành công", orderService.confirmPacking(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy đơn hàng (Staff — bắt buộc lý do)",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> cancel(
        @PathVariable Long id,
        @Valid @RequestBody CancelOrderRequest request
    ) {
        orderService.staffCancelOrder(id, request);
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công"));
    }
}
