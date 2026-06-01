package com.fashionshop.backend.module.returnrequest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateReturnRequest {

    @NotNull
    private Long orderId;

    @NotBlank
    private String reason;

    private List<String> evidenceImages;

    @NotEmpty
    @Valid
    private List<ReturnItemRequest> items;

    @Getter
    @Setter
    public static class ReturnItemRequest {
        @NotNull
        private Long orderItemId;

        @NotNull
        @Min(1)
        private Integer quantity;
    }
}
