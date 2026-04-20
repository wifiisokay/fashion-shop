package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.module.product.dto.request.ProductColorRequest;
import com.fashionshop.backend.module.product.dto.response.ProductColorResponse;
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
@RequestMapping("/api/admin/products/{productId}/colors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
@Tag(name = "Admin Product Colors", description = "CRUD màu sắc sản phẩm")
public class AdminProductColorController {

    private final ProductColorService colorService;

    @GetMapping
    @Operation(summary = "Danh sách màu của sản phẩm", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<List<ProductColorResponse>>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(colorService.getByProductId(productId)));
    }

    @PostMapping
    @Operation(summary = "Thêm màu mới", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductColorResponse>> create(
        @PathVariable Long productId,
        @Valid @RequestBody ProductColorRequest request
    ) {
        ProductColorResponse response = colorService.create(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Thêm màu thành công", response));
    }

    @PutMapping("/{colorId}")
    @Operation(summary = "Cập nhật màu", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductColorResponse>> update(
        @PathVariable Long productId,
        @PathVariable Long colorId,
        @Valid @RequestBody ProductColorRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật màu thành công", colorService.update(productId, colorId, request)));
    }

    @DeleteMapping("/{colorId}")
    @Operation(summary = "Xóa màu (cascade xóa variants + set null images)",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable Long productId,
        @PathVariable Long colorId
    ) {
        colorService.delete(productId, colorId);
        return ResponseEntity.ok(ApiResponse.success("Xóa màu thành công"));
    }
}
