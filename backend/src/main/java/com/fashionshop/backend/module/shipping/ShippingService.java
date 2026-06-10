package com.fashionshop.backend.module.shipping;

import com.fashionshop.backend.module.shipping.dto.request.ShippingFeeRequest;
import com.fashionshop.backend.module.shipping.dto.response.ShippingFeeResponse;

public interface ShippingService {

    /**
     * Calculates GHN Sandbox fee for the selected address and optional items.
     */
    ShippingFeeResponse calculateShippingFee(Long userId, ShippingFeeRequest request);
}
