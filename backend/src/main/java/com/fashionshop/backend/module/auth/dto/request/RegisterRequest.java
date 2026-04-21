package com.fashionshop.backend.module.auth.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự")
    private String password;

    @Pattern(
        regexp = "^(0[3-9])\\d{8}$",
        message = "Số điện thoại không hợp lệ (phải là số VN 10 số, bắt đầu 03-09)"
    )
    private String phone;
}
