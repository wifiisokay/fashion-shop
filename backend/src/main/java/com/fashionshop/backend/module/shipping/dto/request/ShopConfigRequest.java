package com.fashionshop.backend.module.shipping.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body cho PUT /api/admin/shop-config
 */
@Getter
@Setter
public class ShopConfigRequest {

    @NotNull(message = "provinceId không được để trống")
    @Min(value = 1, message = "provinceId phải lớn hơn 0")
    private Integer provinceId;

    @NotNull(message = "districtId không được để trống")
    @Min(value = 1, message = "districtId phải lớn hơn 0")
    private Integer districtId;

    @NotBlank(message = "wardCode không được để trống")
    private String wardCode;

    private String provinceName;
    private String districtName;
    private String wardName;
    private String street;
}
