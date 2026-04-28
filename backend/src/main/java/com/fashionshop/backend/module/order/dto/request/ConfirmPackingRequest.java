package com.fashionshop.backend.module.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Staff xác nhận đóng gói kiện hàng.
 * volumetricWeight và chargeableWeight tự tính ở service.
 */
@Getter
@Setter
public class ConfirmPackingRequest {

    @NotNull(message = "Chiều dài không được để trống")
    @Min(value = 1, message = "Chiều dài tối thiểu 1 cm")
    @Max(value = 200, message = "Chiều dài tối đa 200 cm")
    private Integer length;     // cm

    @NotNull(message = "Chiều rộng không được để trống")
    @Min(value = 1, message = "Chiều rộng tối thiểu 1 cm")
    @Max(value = 200, message = "Chiều rộng tối đa 200 cm")
    private Integer width;      // cm

    @NotNull(message = "Chiều cao không được để trống")
    @Min(value = 1, message = "Chiều cao tối thiểu 1 cm")
    @Max(value = 200, message = "Chiều cao tối đa 200 cm")
    private Integer height;     // cm

    @NotNull(message = "Khối lượng không được để trống")
    @Min(value = 1, message = "Khối lượng tối thiểu 1 gram")
    @Max(value = 30000, message = "Khối lượng tối đa 30 kg")
    private Integer actualWeight;   // gram

    private String packingNote;     // ghi chú đóng gói (optional)
}
