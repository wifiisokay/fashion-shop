package com.fashionshop.backend.module.product.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.fashionshop.backend.common.enums.Gender;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Giá cơ bản không được để trống")
    @DecimalMin(value = "0", inclusive = false, message = "Giá phải lớn hơn 0")
    private BigDecimal basePrice;

    private BigDecimal salePrice;

    @NotNull(message = "isSale không được để trống")
    private Boolean isSale;

    @NotNull(message = "categoryId không được để trống")
    private Integer categoryId;

    @NotNull(message = "Giới tính không được để trống")
    private Gender gender;

    private String material;

    private String colorFamily;

    private List<String> styleTags;

    private List<String> occasionTags;
}
