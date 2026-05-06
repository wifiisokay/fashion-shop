package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/returns")
@RequiredArgsConstructor
@Tag(name = "Returns — Admin", description = "Quản lý yêu cầu trả hàng (Admin only)")
public class AdminReturnController {

    private final ReturnService returnService;

    @PatchMapping("/{id}/receive")
    @Operation(summary = "Xác nhận nhận hàng trả lại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> receive(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        returnService.receiveReturn(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận nhận hàng"));
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Hoàn tất trả hàng + hoàn tiền", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> complete(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @RequestBody Map<String, Object> body
    ) {
        BigDecimal refundAmount = null;
        if (body.containsKey("refundAmount") && body.get("refundAmount") != null) {
            refundAmount = new BigDecimal(body.get("refundAmount").toString());
        }
        returnService.completeReturn(user.getId(), id, refundAmount);
        return ResponseEntity.ok(ApiResponse.success("Đã hoàn tất yêu cầu trả hàng"));
    }
}
