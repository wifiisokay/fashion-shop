package com.fashionshop.backend.module.user;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.user.dto.response.AdminUserResponse;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAdminUsers(keyword, role, page, size)));
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
        userService.toggleUserStatus(id, status, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Đổi trạng thái thành công", null));
    }
}
