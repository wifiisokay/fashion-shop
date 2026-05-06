package com.fashionshop.backend.module.user;

import com.fashionshop.backend.module.user.dto.request.UpdateProfileRequest;
import com.fashionshop.backend.module.user.dto.response.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contract cho nghiệp vụ hồ sơ người dùng hiện tại.
 */
public interface UserService {

    /**
     * Lấy hồ sơ user hiện tại theo email đã xác thực.
     */
    UserProfileResponse getProfileByEmail(String email);

    /**
     * Cập nhật hồ sơ user hiện tại theo email đã xác thực.
     * Chỉ cho phép sửa: fullName, phone, avatarUrl.
     */
    UserProfileResponse updateProfileByEmail(String email, UpdateProfileRequest request);

    /**
     * Upload avatar lên Cloudinary và cập nhật avatarUrl cho user.
     */
    UserProfileResponse uploadAvatarByEmail(String email, MultipartFile file);

    /**
     * Xóa avatar hiện tại của user (Cloudinary + DB).
     */
    UserProfileResponse removeAvatarByEmail(String email);
    // ================================================================
    // ADMIN METHODS
    // ================================================================

    com.fashionshop.backend.common.PageResponse<com.fashionshop.backend.module.user.dto.response.AdminUserResponse> getAdminUsers(String keyword, com.fashionshop.backend.common.enums.Role role, int page, int size);

    com.fashionshop.backend.module.user.dto.response.UserStatsResponse getUserStats();

    void toggleUserStatus(Long userId, com.fashionshop.backend.common.enums.UserStatus newStatus, com.fashionshop.backend.domain.User currentUser);
}
