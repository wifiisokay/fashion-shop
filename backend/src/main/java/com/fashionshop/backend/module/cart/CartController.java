package com.fashionshop.backend.module.cart;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.cart.dto.request.AddToCartRequest;
import com.fashionshop.backend.module.cart.dto.request.UpdateCartRequest;
import com.fashionshop.backend.module.cart.dto.response.CartSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Cart", description = "Quản lý giỏ hàng")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Lấy giỏ hàng hiện tại",
               description = "Trả đầy đủ items, unitPrice, subtotal, stockQuantity, available.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<CartSummaryResponse>> getCart(
        @AuthenticationPrincipal User user
    ) {
        log.info("[CHECKOUT_CART_ACCESS] endpoint=/api/cart userId={} reason=cart_summary_request", user.getId());
        return ResponseEntity.ok(ApiResponse.success(cartService.getCart(user.getId())));
    }

    @PostMapping("/items")
    @Operation(summary = "Thêm variant vào giỏ",
               description = "Nếu variant đã có → cộng dồn quantity (merge). Validate tồn kho.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<CartSummaryResponse>> addItem(
        @AuthenticationPrincipal User user,
        @Valid @RequestBody AddToCartRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Đã thêm vào giỏ hàng", cartService.addItem(user.getId(), request)));
    }

    @PatchMapping("/items/{variantId}")
    @Operation(summary = "Cập nhật số lượng item",
               description = "Set cứng quantity mới (không cộng dồn). Dùng variantId thay vì cartItemId.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<CartSummaryResponse>> updateItem(
        @AuthenticationPrincipal User user,
        @PathVariable Long variantId,
        @Valid @RequestBody UpdateCartRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật số lượng thành công", cartService.updateItem(user.getId(), variantId, request)));
    }

    @DeleteMapping("/items/{variantId}")
    @Operation(summary = "Xóa 1 item khỏi giỏ",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<CartSummaryResponse>> removeItem(
        @AuthenticationPrincipal User user,
        @PathVariable Long variantId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Đã xóa sản phẩm khỏi giỏ hàng", cartService.removeItem(user.getId(), variantId)));
    }

    @DeleteMapping
    @Operation(summary = "Xóa toàn bộ giỏ hàng",
               description = "Trả CartSummaryResponse rỗng. Cũng được gọi nội bộ bởi OrderService.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<CartSummaryResponse>> clearCart(
        @AuthenticationPrincipal User user
    ) {
        cartService.clearCart(user.getId());
        return ResponseEntity.ok(ApiResponse.success(
            "Đã xóa toàn bộ giỏ hàng", CartSummaryResponse.empty()));
    }
}
