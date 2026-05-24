package com.fashionshop.backend.module.order.dto.request;

import com.fashionshop.backend.common.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private OrderStatus status;

    /** Bắt buộc khi status = CANCELLED */
    private String cancelReason;
}
