package com.fashionshop.backend.module.shipping;

import com.fashionshop.backend.module.shipping.dto.request.ShippingFeeRequest;
import com.fashionshop.backend.module.shipping.dto.response.ShippingFeeResponse;

public interface ShippingService {

    /**
     * Tính phí vận chuyển cho địa chỉ đã chọn.
     * @param userId ID user đang đăng nhập (verify ownership)
     * @param request chứa addressId và orderValue
     * @return ShippingFeeResponse (có thể từ cache hoặc fallback)
     */
    ShippingFeeResponse calculateShippingFee(Long userId, ShippingFeeRequest request);
}
