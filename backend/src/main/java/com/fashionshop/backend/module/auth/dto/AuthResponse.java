package com.fashionshop.backend.module.auth.dto;

import com.fashionshop.backend.common.enums.Role;
import lombok.Builder;
import lombok.Getter;

/**
 * Response trả về sau register/login.
 * Token KHÔNG được gửi trong body (bảo mật) — được set vào HttpOnly cookie.
 * Body chỉ chứa thông tin user.
 */
@Getter
@Builder
public class AuthResponse {
    private Long userId;
    private String fullName;
    private String email;
    private Role role;
    private String avatarUrl;
}
