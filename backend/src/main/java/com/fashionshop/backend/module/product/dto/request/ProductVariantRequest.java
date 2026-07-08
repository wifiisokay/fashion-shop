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

    /**
     * Số lượng tồn kho BAN ĐẦU — chỉ bắt buộc khi CREATE (POST).
     * Khi UPDATE (PUT), trường này bị bỏ qua hoàn toàn — service KHÔNG đọc giá trị
     * này để set lại stockQuantity. Validate "bắt buộc khi create" được thực hiện
     * thủ công trong ProductVariantServiceImpl.create(), không dùng @NotNull ở đây
     * vì cùng DTO được tái sử dụng cho cả 2 thao tác.
     */
    @Min(value = 0, message = "Số lượng tồn kho không được âm")
    private Integer stockQuantity;

    /** Override giá riêng cho variant này. NULL = dùng basePrice của Product. */
    private BigDecimal price;
}