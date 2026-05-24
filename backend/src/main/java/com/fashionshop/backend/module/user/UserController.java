package com.fashionshop.backend.module.user;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.user.dto.request.UpdateProfileRequest;
import com.fashionshop.backend.module.user.dto.response.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User Profile", description = "Quản lý hồ sơ người dùng hiện tại")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Lấy hồ sơ người dùng",
        description = "Lấy thông tin profile của user hiện tại từ email trong principal.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy profile thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy user")
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
        @AuthenticationPrincipal User currentUser
    ) {
        String currentEmail = validateAndGetEmail(currentUser);
        return ResponseEntity.ok(ApiResponse.success(userService.getProfileByEmail(currentEmail)));
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Cập nhật hồ sơ người dùng",
        description = "Chỉ cho phép cập nhật fullName, phone, avatarUrl.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật profile thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy user")
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
        @AuthenticationPrincipal User currentUser,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        String currentEmail = validateAndGetEmail(currentUser);
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật hồ sơ thành công",
            userService.updateProfileByEmail(currentEmail, request)
        ));
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Upload avatar",
        description = "Upload ảnh avatar lên Cloudinary và cập nhật avatarUrl cho user hiện tại.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Upload avatar thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "File không hợp lệ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy user")
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> uploadAvatar(
        @AuthenticationPrincipal User currentUser,
        @RequestParam("file") MultipartFile file
    ) {
        String currentEmail = validateAndGetEmail(currentUser);
        return ResponseEntity.ok(ApiResponse.success(
            "Upload avatar thành công",
            userService.uploadAvatarByEmail(currentEmail, file)
        ));
    }

    @DeleteMapping("/profile/avatar")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Xóa avatar",
        description = "Xóa avatar hiện tại khỏi Cloudinary và reset avatarUrl về null.",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Xóa avatar thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy user")
    })
    public ResponseEntity<ApiResponse<UserProfileResponse>> removeAvatar(
        @AuthenticationPrincipal User currentUser
    ) {
        String currentEmail = validateAndGetEmail(currentUser);
        return ResponseEntity.ok(ApiResponse.success(
            "Xóa avatar thành công",
            userService.removeAvatarByEmail(currentEmail)
        ));
    }

    private String validateAndGetEmail(User currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        return currentUser.getUsername();
    }
}
