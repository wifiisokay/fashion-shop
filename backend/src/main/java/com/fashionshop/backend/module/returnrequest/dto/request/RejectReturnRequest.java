package com.fashionshop.backend.module.returnrequest.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectReturnRequest {

    @NotBlank(message = "Vui lòng nhập lý do từ chối")
    private String note;
}
