package com.fashionshop.backend.module.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductColorRequest {

    @NotBlank(message = "Tên màu không được để trống")
    @Size(max = 50, message = "Tên màu tối đa 50 ký tự")
    private String colorName;

    /** Mã màu hex, VD: "#1A1A1A". Nullable. */
    @Size(max = 7, message = "Mã màu tối đa 7 ký tự")
    private String colorCode;

    private Integer displayOrder = 0;
}
