package com.fashionshop.backend.module.product.dto.request;

import com.fashionshop.backend.common.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductStatusRequest {

    @NotNull(message = "status không được để trống")
    private ProductStatus status;
}
