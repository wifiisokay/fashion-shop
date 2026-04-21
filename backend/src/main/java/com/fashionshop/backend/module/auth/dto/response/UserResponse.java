package com.fashionshop.backend.module.auth.dto.response;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {
    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private String avatarUrl;
    private UserStatus status;
}
