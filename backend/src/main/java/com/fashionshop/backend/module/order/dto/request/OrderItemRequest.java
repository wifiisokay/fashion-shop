package com.fashionshop.backend.module.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItemRequest {

    @NotNull(message = "Vui long chon bien the san pham")
    private Long variantId;

    @Min(value = 1, message = "So luong phai lon hon 0")
    private int quantity;
}
