package com.fashionshop.backend.module.returnrequest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReturnRequest {

    @NotNull(message = "Mã đơn hàng không được để trống")
    private Long orderId;

    @NotBlank(message = "Lý do trả hàng không được để trống")
    @Size(max = 500, message = "Lý do trả hàng tối đa 500 ký tự")
    private String reason;
}
