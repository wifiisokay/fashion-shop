package com.fashionshop.backend.module.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fashionshop.backend.domain.Address;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Response trả về cho client sau mọi thao tác với địa chỉ.
 * Dùng static factory fromEntity() để map từ entity — tránh expose entity trực tiếp.
 */
@Getter
@Builder
public class AddressResponse {

    private Long id;
    private String fullName;
    private String phone;

    private String province;
    private int provinceCode;

    private String district;
    private int districtCode;

    private String ward;
    private String wardCode;    // String — GHN dạng "20194"

    private String street;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private LocalDateTime createdAt;

    /**
     * Static factory — map trực tiếp từ Address entity.
     * Sử dụng thay cho MapStruct để giảm phụ thuộc trong module đơn giản.
     */
    public static AddressResponse fromEntity(Address address) {
        return AddressResponse.builder()
            .id(address.getId())
            .fullName(address.getFullName())
            .phone(address.getPhone())
            .province(address.getProvince())
            .provinceCode(address.getProvinceCode())
            .district(address.getDistrict())
            .districtCode(address.getDistrictCode())
            .ward(address.getWard())
            .wardCode(address.getWardCode())
            .street(address.getStreet())
            .isDefault(address.isDefault())
            .createdAt(address.getCreatedAt())
            .build();
    }
}
