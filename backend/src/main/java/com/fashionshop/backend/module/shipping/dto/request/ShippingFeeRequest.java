package com.fashionshop.backend.module.shipping.dto.request;

import java.util.List;

import com.fashionshop.backend.module.order.dto.request.OrderItemRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ShippingFeeRequest {

    @NotNull(message = "Vui lòng chọn địa chỉ giao hàng")
    private Long addressId;

    /** Optional insurance value sent to GHN. Backend computes it from items when possible. */
    private Long orderValue;

    /** Kept for backward compatibility; product-level estimation is preferred. */
    private Integer totalWeight;

    @Valid
    private List<OrderItemRequest> items;
}
