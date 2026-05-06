package com.fashionshop.backend.module.order.dto.response;

import com.fashionshop.backend.domain.Order;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OrderSummaryResponse {

    private Long id;
    private String status;
    private String statusLabel;
    private String paymentMethod;
    private BigDecimal totalAmount;
    private int itemCount;
    private String firstItemName;
    private String firstItemImageUrl;
    private String customerName;
    private LocalDateTime createdAt;

    public static OrderSummaryResponse from(Order order, String statusLabel) {
        var items = order.getItems();
        return OrderSummaryResponse.builder()
            .id(order.getId())
            .status(order.getStatus().name())
            .statusLabel(statusLabel)
            .paymentMethod(order.getPaymentMethod().name())
            .totalAmount(order.getTotalAmount())
            .itemCount(items != null ? items.size() : 0)
            .firstItemName(items != null && !items.isEmpty() ? items.get(0).getProductName() : null)
            .firstItemImageUrl(items != null && !items.isEmpty() ? items.get(0).getImageUrl() : null)
            .customerName(order.getUser() != null ? order.getUser().getFullName() : null)
            .createdAt(order.getCreatedAt())
            .build();
    }
}
