package com.fashionshop.backend.module.order;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.order.dto.request.CancelOrderRequest;
import com.fashionshop.backend.module.order.dto.request.CreateOrderRequest;
import com.fashionshop.backend.module.order.dto.response.CreateOrderResponse;
import com.fashionshop.backend.module.order.dto.response.OrderDetailResponse;
import com.fashionshop.backend.module.order.dto.response.OrderSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders — Customer", description = "Đặt hàng và quản lý đơn hàng của khách hàng")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Tạo đơn hàng từ giỏ hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<CreateOrderResponse>> create(
        @AuthenticationPrincipal User user,
        @Valid @RequestBody CreateOrderRequest request,
        jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String ipAddress = httpRequest.getRemoteAddr();
        CreateOrderResponse response = orderService.createOrder(user.getId(), request, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Đặt hàng thành công", response));
    }

    @GetMapping
    @Operation(summary = "Danh sách đơn hàng của tôi", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getMyOrders(
        @AuthenticationPrincipal User user,
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            orderService.getMyOrders(user.getId(), status, keyword, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết đơn hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getById(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            orderService.getMyOrderById(user.getId(), id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy đơn hàng (Customer)", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> cancel(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @RequestBody(required = false) CancelOrderRequest request
    ) {
        orderService.cancelOrder(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công"));
    }

}
