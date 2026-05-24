package com.fashionshop.backend.module.shipping.dto.ghn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * DTO gửi lên GHN API /v2/shipping-order/fee.
 * Dùng snake_case vì GHN API yêu cầu.
 */
@Getter
@Builder
public class GhnFeeRequest {

    @JsonProperty("service_id")
    private int serviceId;

    @JsonProperty("insurance_value")
    private long insuranceValue;

    @JsonProperty("from_district_id")
    private int fromDistrictId;

    @JsonProperty("from_ward_code")
    private String fromWardCode;

    @JsonProperty("to_district_id")
    private int toDistrictId;

    @JsonProperty("to_ward_code")
    private String toWardCode;

    private int weight;
    private int length;
    private int width;
    private int height;
}
