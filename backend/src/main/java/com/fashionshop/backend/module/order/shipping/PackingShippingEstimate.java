package com.fashionshop.backend.module.order.shipping;

import java.math.BigDecimal;
import java.util.List;

public record PackingShippingEstimate(
    int volumetricWeight,
    int chargeableWeight,
    BigDecimal estimatedShippingFee,
    BigDecimal shippingFeeDifference,
    List<String> warnings
) {}
