package com.fashionshop.backend.module.user.dto.request;

import com.fashionshop.backend.common.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRoleRequest {

    @NotNull(message = "Role không được để trống")
    private Role role;
}
