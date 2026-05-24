package com.fashionshop.backend.module.shipping;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.domain.ShopConfig;
import com.fashionshop.backend.domain.repository.ShopConfigRepository;
import com.fashionshop.backend.module.shipping.dto.request.ShopConfigRequest;
import com.fashionshop.backend.module.shipping.dto.response.ShopConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/shop-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Shop Config", description = "Cấu hình kho hàng (Admin)")
public class ShopConfigController {

    private final ShopConfigRepository shopConfigRepository;

    @GetMapping
    @Operation(summary = "Lấy cấu hình kho hàng hiện tại",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ShopConfigResponse>> getConfig() {
        ShopConfig config = shopConfigRepository.getConfig();
        return ResponseEntity.ok(ApiResponse.success(ShopConfigResponse.from(config)));
    }

    @PutMapping
    @Operation(summary = "Cập nhật địa chỉ kho hàng",
               description = "Cập nhật district_id và ward_code (theo mã GHN master-data). " +
                             "Thay đổi sẽ ảnh hưởng ngay lập tức tới tính phí ship cho mọi đơn mới.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ShopConfigResponse>> updateConfig(
            @Valid @RequestBody ShopConfigRequest request
    ) {
        ShopConfig config = shopConfigRepository.getConfig();
        config.setProvinceId(request.getProvinceId());
        config.setDistrictId(request.getDistrictId());
        config.setWardCode(request.getWardCode());
        config.setProvinceName(request.getProvinceName());
        config.setDistrictName(request.getDistrictName());
        config.setWardName(request.getWardName());
        config.setStreet(request.getStreet());

        shopConfigRepository.save(config);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật kho hàng thành công", ShopConfigResponse.from(config)));
    }
}
