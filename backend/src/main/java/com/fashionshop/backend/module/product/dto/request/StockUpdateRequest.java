package com.fashionshop.backend.module.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body cho PATCH /api/admin/products/{productId}/variants/{variantId}/stock
 *
 * Chỉ nhận số lượng TĂNG THÊM (addedStock), không cho phép ghi đè trực tiếp
 * stockQuantity — tránh admin vô tình đặt sai số làm mất dữ liệu tồn kho thực tế
 * (ví dụ: đơn hàng vừa trừ kho song song với lúc admin sửa).
 *
 * Để giảm tồn kho (hàng lỗi, thất lạc...), cần một luồng riêng có audit log,
 * không nằm trong phạm vi endpoint này.
 */
@Getter
@Setter
public class StockUpdateRequest {

    @NotNull(message = "Số lượng nhập thêm không được để trống")
    @Min(value = 1, message = "Số lượng nhập thêm phải lớn hơn 0")
    private Integer addedStock;
}