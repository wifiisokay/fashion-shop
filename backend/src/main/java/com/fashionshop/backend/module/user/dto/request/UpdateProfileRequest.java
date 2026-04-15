package com.fashionshop.backend.module.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String fullName;

    @Pattern(
        regexp = "^(0[3|5|7|8|9])+([0-9]{8})$",
        message = "Số điện thoại không hợp lệ (VN 10 số, bắt đầu 03/05/07/08/09)"
    )
    private String phone;
}
