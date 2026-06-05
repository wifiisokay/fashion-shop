package com.fashionshop.backend.module.shipping;

import java.time.LocalDate;

public record CheckoutShippingFeeResult(
    String provider,
    long shippingFee,
    int estimatedWeight,
    int packageLength,
    int packageWidth,
    int packageHeight,
    int serviceId,
    int estimatedDays,
    String estimatedDateText,
    LocalDate expectedDeliveryDate,
    boolean cached
) {}
