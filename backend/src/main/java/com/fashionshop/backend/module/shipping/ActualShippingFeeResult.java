package com.fashionshop.backend.module.shipping;

import java.math.BigDecimal;

public record ActualShippingFeeResult(
    BigDecimal actualShippingFee,
    BigDecimal shippingFeeDifference,
    int actualWeight,
    int packageLength,
    int packageWidth,
    int packageHeight
) {}
