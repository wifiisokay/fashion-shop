package com.fashionshop.backend.module.shipping;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.shipping.dto.request.ShippingFeeRequest;
import com.fashionshop.backend.module.shipping.dto.response.ShippingFeeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Shipping", description = "Tính phí vận chuyển GHN")
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping("/fee")
    @Operation(summary = "Tính phí vận chuyển",
               description = "Gọi GHN API tính phí ship dựa trên địa chỉ đã chọn. " +
                             "Kết quả được cache 5 phút theo district:ward. " +
                             "Nếu GHN lỗi, trả fallback fee 30.000đ.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ShippingFeeResponse>> calculateFee(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ShippingFeeRequest request
    ) {
        ShippingFeeResponse response = shippingService.calculateShippingFee(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
