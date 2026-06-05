package com.fashionshop.backend.module.product.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class StockAlertItem {
    private Long productId;
    private String productName;
    private Long colorId;
    private String colorName;
    private Long variantId;
    private String size;
    private Integer stockQuantity;
    private Integer threshold;
}
