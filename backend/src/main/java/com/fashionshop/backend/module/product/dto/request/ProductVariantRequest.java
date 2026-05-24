package com.fashionshop.backend.module.product.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductVariantRequest {

    @NotNull(message = "colorId không được để trống")
    private Long colorId;

    @NotBlank(message = "Size không được để trống")
    @Size(max = 20, message = "Size tối đa 20 ký tự")
    private String size;

    @NotNull(message = "Số lượng tồn kho không được để trống")
    @Min(value = 0, message = "Số lượng tồn kho không được âm")
    private Integer stockQuantity;

    /** Override giá riêng cho variant này. NULL = dùng basePrice của Product. */
    private BigDecimal price;
}
