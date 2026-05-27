package com.fashionshop.backend.module.product.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProductSaleRequest {
    private Boolean isSale;
    private BigDecimal salePrice;
    private LocalDateTime saleStartAt;
    private LocalDateTime saleEndAt;
}
