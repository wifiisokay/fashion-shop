package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnDashboardResponse;
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
@Tag(name = "Returns — Admin", description = "Quản lý yêu cầu đổi/trả hoặc khiếu nại (Admin only)")
public class AdminReturnController {

    private final ReturnService returnService;

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard quản lý yêu cầu đổi/trả hoặc khiếu nại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(returnService.getAdminDashboard()));
    }

    @PatchMapping("/{id}/receive")
    @Operation(summary = "Xác nhận đã nhận hàng/ghi nhận xử lý", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> receive(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        returnService.receiveReturn(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận đã nhận hàng/ghi nhận xử lý"));
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Hoàn tất xử lý yêu cầu đổi/trả hoặc khiếu nại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> complete(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @RequestBody Map<String, Object> body
    ) {
        BigDecimal refundAmount = null;
        if (body.containsKey("refundAmount") && body.get("refundAmount") != null) {
            refundAmount = new BigDecimal(body.get("refundAmount").toString());
        }
        String note = body.containsKey("note") && body.get("note") != null
            ? body.get("note").toString()
            : (body.containsKey("adminNote") && body.get("adminNote") != null ? body.get("adminNote").toString() : null);
        returnService.completeReturn(user.getId(), id, refundAmount, note);
        return ResponseEntity.ok(ApiResponse.success("Đã hoàn tất xử lý yêu cầu"));
    }
}
