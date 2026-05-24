package com.fashionshop.backend.module.user.dto.response;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserProfileResponse {

    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private String avatarUrl;
    private UserStatus status;
    private LocalDateTime createdAt;

    public static UserProfileResponse fromEntity(User user) {
        return UserProfileResponse.builder()
            .userId(user.getId())
            .fullName(user.getFullName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .role(user.getRole())
            .avatarUrl(user.getAvatarUrl())
            .status(user.getStatus())
            .createdAt(user.getCreatedAt())
            .build();
    }
}
