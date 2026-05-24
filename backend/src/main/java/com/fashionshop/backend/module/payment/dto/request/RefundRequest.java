package com.fashionshop.backend.module.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundRequest {

    @NotBlank(message = "Vui lòng nhập lý do hoàn tiền")
    @Size(max = 500, message = "Lý do hoàn tiền tối đa 500 ký tự")
    private String reason;
}
