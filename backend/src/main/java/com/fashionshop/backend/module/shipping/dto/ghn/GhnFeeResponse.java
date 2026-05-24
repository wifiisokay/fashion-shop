package com.fashionshop.backend.module.shipping.dto.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO parse response từ GHN API.
 * GHN trả shape: { code: 200, message: "Success", data: { total: ..., expected_delivery_time: ... } }
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnFeeResponse {

    private int code;
    private String message;
    private GhnFeeData data;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GhnFeeData {
        /** Tổng phí ship (VND) */
        private long total;

        /** Thời gian giao dự kiến (ISO datetime string hoặc epoch) */
        @JsonProperty("expected_delivery_time")
        private String expectedDeliveryTime;
    }
}
