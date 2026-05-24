package com.fashionshop.backend.module.order.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelOrderRequest {

    /** Customer: tùy chọn. Staff: bắt buộc (validate trong service). */
    private String reason;
}
