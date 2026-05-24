package com.fashionshop.backend.module.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body cho PATCH /api/admin/products/{productId}/variants/{variantId}/stock
 * Chỉ cho phép cập nhật số lượng tồn kho — tách biệt với ProductVariantRequest
 * để EMPLOYEE không thể thay đổi giá/màu/size.
 */
@Getter
@Setter
public class StockUpdateRequest {

    @NotNull(message = "Số lượng tồn kho không được để trống")
    @Min(value = 0, message = "Số lượng tồn kho không được âm")
    private Integer stockQuantity;
}
