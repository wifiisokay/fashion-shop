package com.fashionshop.backend.module.shipping;

import com.fashionshop.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Proxy GHN master-data API cho frontend (public).
 * Tránh expose GHN token ra client.
 * Dùng chung cho cả Customer (AddressManager) và Admin (ShopSettings).
 */
@RestController
@RequestMapping("/api/ghn")
@RequiredArgsConstructor
@Tag(name = "GHN Master Data", description = "Proxy GHN master-data cho address form")
public class GhnMasterDataController {

    private final GhnApiClient ghnApiClient;

    @GetMapping("/provinces")
    @Operation(summary = "Danh sách tỉnh/thành (GHN)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProvinces() {
        return ResponseEntity.ok(ApiResponse.success(ghnApiClient.getProvinces()));
    }

    @GetMapping("/districts")
    @Operation(summary = "Danh sách quận/huyện theo tỉnh (GHN)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDistricts(
            @RequestParam("province_id") int provinceId
    ) {
        return ResponseEntity.ok(ApiResponse.success(ghnApiClient.getDistricts(provinceId)));
    }

    @GetMapping("/wards")
    @Operation(summary = "Danh sách phường/xã theo quận (GHN)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getWards(
            @RequestParam("district_id") int districtId
    ) {
        return ResponseEntity.ok(ApiResponse.success(ghnApiClient.getWards(districtId)));
    }
}
