package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.returnrequest.dto.request.RejectReturnRequest;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnDashboardResponse;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/returns")
@RequiredArgsConstructor
@Tag(name = "Returns — Staff", description = "Quản lý yêu cầu đổi/trả hoặc khiếu nại (Staff/Admin)")
public class StaffReturnController {

    private final ReturnService returnService;

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard xử lý yêu cầu đổi/trả hoặc khiếu nại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(returnService.getStaffDashboard()));
    }

    @GetMapping
    @Operation(summary = "Danh sách yêu cầu đổi/trả hoặc khiếu nại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<PageResponse<ReturnResponse>>> getAll(
        @RequestParam(required = false) ReturnStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            returnService.getAllReturns(status, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết yêu cầu đổi/trả hoặc khiếu nại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ReturnResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(returnService.getReturnById(id)));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Duyệt yêu cầu đổi/trả hoặc khiếu nại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> approve(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String note = (body != null) ? body.get("note") : null;
        returnService.approveReturn(user.getId(), id, note);
        return ResponseEntity.ok(ApiResponse.success("Đã duyệt yêu cầu"));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Từ chối yêu cầu đổi/trả hoặc khiếu nại", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> reject(
        @AuthenticationPrincipal User user,
        @PathVariable Long id,
        @Valid @RequestBody RejectReturnRequest request
    ) {
        returnService.rejectReturn(user.getId(), id, request.getNote());
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối yêu cầu"));
    }
}
