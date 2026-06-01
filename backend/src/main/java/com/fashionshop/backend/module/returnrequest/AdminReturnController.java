package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.returnrequest.dto.request.AdminActionRequest;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnDashboardResponse;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    @Operation(summary = "Danh sách yêu cầu trả hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<PageResponse<ReturnResponse>>> list(
        @RequestParam(required = false) ReturnStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getAllReturns(status, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết yêu cầu trả hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getReturnById(id)));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Duyệt yêu cầu trả hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> approve(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @Valid @RequestBody AdminActionRequest request
    ) {
        returnService.approveReturn(user.getId(), id, request.getNote());
        return ResponseEntity.ok(ApiResponse.success("Đã duyệt yêu cầu trả hàng"));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Từ chối yêu cầu trả hàng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> reject(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @Valid @RequestBody AdminActionRequest request
    ) {
        returnService.rejectReturn(user.getId(), id, request.getNote());
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối yêu cầu trả hàng"));
    }

    @PutMapping("/{id}/received")
    @Operation(summary = "Xác nhận đã nhận hàng trả", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> markReceived(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @Valid @RequestBody(required = false) AdminActionRequest request
    ) {
        returnService.markReceived(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận nhận hàng trả"));
    }

    @PutMapping("/{id}/completed")
    @Operation(summary = "Xác nhận đã hoàn tiền", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> completeReturn(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @Valid @RequestBody(required = false) AdminActionRequest request
    ) {
        returnService.completeReturn(user.getId(), id, request != null ? request.getNote() : null);
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận hoàn tiền"));
    }
}
