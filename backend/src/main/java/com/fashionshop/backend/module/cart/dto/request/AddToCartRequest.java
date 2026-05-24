package com.fashionshop.backend.module.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddToCartRequest {

    @NotNull(message = "variantId không được để trống")
    private Long variantId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1,  message = "Số lượng tối thiểu là 1")
    @Max(value = 99, message = "Số lượng tối đa là 99")
    private Integer quantity;
}
