package com.fashionshop.backend.module.shipping.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ShippingFeeResponse {

    private String provider;

    /** Backward-compatible fee field for existing clients. */
    private long fee;

    private long shippingFee;
    private int estimatedWeight;
    private int packageLength;
    private int packageWidth;
    private int packageHeight;
    private int serviceId;
    private int estimatedDays;
    private String estimatedDateText;
    private String serviceName;
    private boolean cached;
    private boolean fallback;
}
