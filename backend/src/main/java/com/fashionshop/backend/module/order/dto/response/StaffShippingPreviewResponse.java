package com.fashionshop.backend.module.order.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StaffShippingPreviewResponse {
    private String provider;
    private BigDecimal customerShippingFee;
    private BigDecimal actualGhnFee;
    private BigDecimal difference;
    private Integer actualWeight;
    private Integer packageLength;
    private Integer packageWidth;
    private Integer packageHeight;
}
