package com.fashionshop.backend.module.product.dto.request;

import com.fashionshop.backend.common.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request gợi ý tag AI — admin gửi khi điền form tạo/sửa sản phẩm.
 */
@Getter
@Setter
public class ProductTagSuggestRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    @NotBlank(message = "Mô tả không được để trống (cần ít nhất 20 ký tự)")
    private String description;

    @NotNull(message = "Giới tính không được để trống")
    private Gender gender;

    /** Tùy chọn — nếu có sẽ giúp AI chính xác hơn */
    private Integer categoryId;
    private String categoryName;
    private String material;
}
