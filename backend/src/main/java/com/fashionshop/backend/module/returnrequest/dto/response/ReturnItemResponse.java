package com.fashionshop.backend.module.returnrequest.dto.response;

import com.fashionshop.backend.domain.ReturnItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ReturnItemResponse {

    private Long id;
    private Long orderItemId;
    private Long productId;
    private Long variantId;
    private String productName;
    private String colorName;
    private String size;
    private String imageUrl;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    public static ReturnItemResponse from(ReturnItem item) {
        return ReturnItemResponse.builder()
            .id(item.getId())
            .orderItemId(item.getOrderItem() != null ? item.getOrderItem().getId() : null)
            .productId(item.getProductId())
            .variantId(item.getVariantId())
            .productName(item.getProductName())
            .colorName(item.getColorName())
            .size(item.getSize())
            .imageUrl(item.getImageUrl())
            .quantity(item.getQuantity())
            .unitPrice(item.getUnitPrice())
            .subtotal(item.getSubtotal())
            .build();
    }
}
