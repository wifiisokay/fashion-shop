package com.fashionshop.backend.module.order.dto.request;

import com.fashionshop.backend.common.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateOrderRequest {

    @NotNull(message = "Vui long chon dia chi giao hang")
    private Long addressId;

    @NotNull(message = "Vui long chon phuong thuc thanh toan")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Phi van chuyen khong duoc de trong")
    @Min(value = 0, message = "Phi van chuyen khong hop le")
    @Max(value = 500000, message = "Phi van chuyen khong hop le")
    private Long shippingFee;

    private String note;

    @Min(value = 1, message = "So ngay giao du kien khong hop le")
    @Max(value = 30, message = "So ngay giao du kien khong hop le")
    private Integer estimatedDays;

    @Valid
    @NotEmpty(message = "Don hang phai co it nhat 1 san pham")
    private List<OrderItemRequest> items;
}
