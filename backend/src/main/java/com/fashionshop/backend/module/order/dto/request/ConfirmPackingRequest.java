package com.fashionshop.backend.module.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmPackingRequest {

    @NotNull(message = "Package length is required")
    @Min(value = 1, message = "Package length must be greater than 0")
    @Max(value = 200, message = "Package length must be at most 200 cm")
    private Integer packageLength;

    @NotNull(message = "Package width is required")
    @Min(value = 1, message = "Package width must be greater than 0")
    @Max(value = 200, message = "Package width must be at most 200 cm")
    private Integer packageWidth;

    @NotNull(message = "Package height is required")
    @Min(value = 1, message = "Package height must be greater than 0")
    @Max(value = 200, message = "Package height must be at most 200 cm")
    private Integer packageHeight;

    @NotNull(message = "Actual weight is required")
    @Min(value = 1, message = "Actual weight must be greater than 0")
    @Max(value = 30000, message = "Actual weight must be at most 30 kg")
    private Integer actualWeight;

    private String packingNote;
}
