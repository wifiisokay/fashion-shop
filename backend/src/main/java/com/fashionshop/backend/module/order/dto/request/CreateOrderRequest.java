package com.fashionshop.backend.module.order.dto.request;

import com.fashionshop.backend.common.enums.PaymentMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    @NotNull(message = "Vui lòng chọn địa chỉ giao hàng")
    private Long addressId;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Phí vận chuyển không được để trống")
    @Min(value = 0, message = "Phí vận chuyển không hợp lệ")
    @Max(value = 500000, message = "Phí vận chuyển không hợp lệ")
    private Long shippingFee;

    private String note;

    /** Số ngày giao dự kiến (từ GHN API response) */
    @Min(value = 1, message = "Số ngày giao dự kiến không hợp lệ")
    @Max(value = 30, message = "Số ngày giao dự kiến không hợp lệ")
    private Integer estimatedDays;
}
