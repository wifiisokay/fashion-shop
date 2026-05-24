package com.fashionshop.backend.module.user;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.user.dto.request.CreateStaffRequest;
import com.fashionshop.backend.module.user.dto.request.UpdateUserRoleRequest;
import com.fashionshop.backend.module.user.dto.request.UpdateUserStatusRequest;
import com.fashionshop.backend.module.user.dto.response.AdminUserResponse;
import com.fashionshop.backend.module.user.dto.response.CreateStaffResponse;
import com.fashionshop.backend.module.user.dto.response.UserStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin User Management", description = "Quản lý người dùng dành cho Admin")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách người dùng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getAdminUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAdminUsers(keyword, role, status, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết người dùng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUserDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAdminUserDetail(id)));
    }

    @PostMapping("/staff")
    @Operation(summary = "Tạo tài khoản nhân viên", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<CreateStaffResponse>> createStaff(
            @RequestBody @jakarta.validation.Valid CreateStaffRequest request) {
        CreateStaffResponse response = userService.createStaff(request);
        String message = response.getTempPassword() != null
            ? "Tạo nhân viên thành công. Mật khẩu tạm: " + response.getTempPassword()
            : "Tạo nhân viên thành công";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Đổi quyền người dùng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserRole(
            @PathVariable Long id,
            @RequestBody @jakarta.validation.Valid UpdateUserRoleRequest request,
            @AuthenticationPrincipal User currentUser) {
        AdminUserResponse response = userService.updateUserRole(id, request.getRole(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Đổi quyền thành công", response));
    }

    @GetMapping("/stats")
    @Operation(summary = "Lấy thống kê người dùng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<UserStatsResponse>> getUserStats() {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserStats()));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Đổi trạng thái tài khoản người dùng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> toggleUserStatus(
            @PathVariable Long id,
            @RequestParam UserStatus status,
            @AuthenticationPrincipal User currentUser) {
        userService.updateUserStatus(id, status, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Đổi trạng thái thành công", null));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Đổi trạng thái tài khoản người dùng", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserStatus(
            @PathVariable Long id,
            @RequestBody @jakarta.validation.Valid UpdateUserStatusRequest request,
            @AuthenticationPrincipal User currentUser) {
        AdminUserResponse response = userService.updateUserStatus(id, request.getStatus(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Đổi trạng thái thành công", response));
    }
}
