package com.fashionshop.backend.module.order.dto.response;

import com.fashionshop.backend.common.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CreateOrderResponse {

    private Long orderId;
    private OrderStatus status;
    private BigDecimal totalAmount;

    /** VNPay: payment URL để redirect. COD: null. */
    private String paymentUrl;
}
