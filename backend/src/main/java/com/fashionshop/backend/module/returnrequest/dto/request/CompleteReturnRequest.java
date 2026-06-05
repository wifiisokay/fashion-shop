package com.fashionshop.backend.module.returnrequest.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CompleteReturnRequest {

    @DecimalMin(value = "0.0", inclusive = true, message = "Số tiền xử lý không được âm")
    private BigDecimal refundAmount;

    private String note;
}
