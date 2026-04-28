package com.fashionshop.backend.module.order.dto.response;

import com.fashionshop.backend.domain.OrderItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OrderItemResponse {

    private Long id;
    private Long variantId;
    private Long productId;
    private String productName;
    private String colorName;
    private String size;
    private String imageUrl;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;

    public static OrderItemResponse from(OrderItem item) {
        return OrderItemResponse.builder()
            .id(item.getId())
            .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
            .productId(item.getProductId())
            .productName(item.getProductName())
            .colorName(item.getColorName())
            .size(item.getSize())
            .imageUrl(item.getImageUrl())
            .unitPrice(item.getUnitPrice())
            .quantity(item.getQuantity())
            .subtotal(item.getSubtotal())
            .build();
    }
}
