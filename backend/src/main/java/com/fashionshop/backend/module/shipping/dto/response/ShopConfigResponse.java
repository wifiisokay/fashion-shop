package com.fashionshop.backend.module.shipping.dto.response;

import com.fashionshop.backend.domain.ShopConfig;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Response cho GET /api/admin/shop-config
 */
@Getter
@Builder
public class ShopConfigResponse {
    private int provinceId;
    private int districtId;
    private String wardCode;
    private String provinceName;
    private String districtName;
    private String wardName;
    private String street;
    private LocalDateTime updatedAt;

    public static ShopConfigResponse from(ShopConfig config) {
        return ShopConfigResponse.builder()
                .provinceId(config.getProvinceId())
                .districtId(config.getDistrictId())
                .wardCode(config.getWardCode())
                .provinceName(config.getProvinceName())
                .districtName(config.getDistrictName())
                .wardName(config.getWardName())
                .street(config.getStreet())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
