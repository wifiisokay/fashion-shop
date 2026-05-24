package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.module.product.dto.request.ProductVariantRequest;
import com.fashionshop.backend.module.product.dto.request.StockUpdateRequest;
import com.fashionshop.backend.module.product.dto.response.ProductVariantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products/{productId}/variants")
@RequiredArgsConstructor
@Tag(name = "Admin Product Variants", description = "CRUD biến thể sản phẩm")
public class AdminVariantController {

    private final ProductVariantService variantService;

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    @Operation(summary = "Danh sách variant của sản phẩm", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(variantService.getByProductId(productId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thêm variant mới", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductVariantResponse>> create(
        @PathVariable Long productId,
        @Valid @RequestBody ProductVariantRequest request
    ) {
        ProductVariantResponse response = variantService.create(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Thêm biến thể thành công", response));
    }

    @PutMapping("/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật variant", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductVariantResponse>> update(
        @PathVariable Long productId,
        @PathVariable Long variantId,
        @Valid @RequestBody ProductVariantRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật biến thể thành công", variantService.update(productId, variantId, request)));
    }

    @DeleteMapping("/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa variant", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable Long productId,
        @PathVariable Long variantId
    ) {
        variantService.delete(productId, variantId);
        return ResponseEntity.ok(ApiResponse.success("Xóa biến thể thành công"));
    }

    @PatchMapping("/{variantId}/stock")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    @Operation(summary = "Cập nhật tồn kho variant",
               description = "Chỉ cập nhật số lượng tồn kho. Dành cho nhân viên kho (EMPLOYEE) và Admin.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateStock(
        @PathVariable Long productId,
        @PathVariable Long variantId,
        @Valid @RequestBody StockUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật tồn kho thành công", variantService.updateStock(productId, variantId, request)));
    }
}

