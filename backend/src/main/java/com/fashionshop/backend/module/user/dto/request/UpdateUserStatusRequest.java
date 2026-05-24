package com.fashionshop.backend.module.user.dto.request;

import com.fashionshop.backend.common.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private UserStatus status;
}
