package com.fashionshop.backend.module.shipping.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request từ frontend: gửi addressId để tính phí ship.
 * orderValue dùng để tính insurance (tùy chọn).
 */
@Getter
@Setter
@NoArgsConstructor
public class ShippingFeeRequest {

    @NotNull(message = "Vui lòng chọn địa chỉ giao hàng")
    private Long addressId;

    /** Tổng giá trị đơn hàng (VND) — dùng tính bảo hiểm GHN. Mặc định 0 */
    private Long orderValue;

    /** Tổng cân nặng ước tính (gram) — tính từ giỏ hàng. Null → dùng default */
    private Integer totalWeight;
}
