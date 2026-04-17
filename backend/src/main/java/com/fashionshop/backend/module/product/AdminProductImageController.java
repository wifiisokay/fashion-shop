package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.module.product.dto.response.ProductImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products/{productId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
@Tag(name = "Admin Product Images", description = "Upload/xóa ảnh sản phẩm")
public class AdminProductImageController {

    private final ProductImageService imageService;

    @GetMapping
    @Operation(summary = "Danh sách ảnh của sản phẩm", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(imageService.getByProductId(productId)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload ảnh sản phẩm",
               description = "Upload ảnh lên Cloudinary. isPrimary=true sẽ clear primary cũ.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductImageResponse>> upload(
        @PathVariable Long productId,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "variantId", required = false) Long variantId,
        @RequestParam(value = "isPrimary", defaultValue = "false") Boolean isPrimary
    ) {
        ProductImageResponse response = imageService.upload(productId, file, variantId, isPrimary);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Upload ảnh thành công", response));
    }

    @PatchMapping("/{imageId}/primary")
    @Operation(summary = "Đặt ảnh làm primary", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductImageResponse>> setPrimary(
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Đặt ảnh chính thành công", imageService.setPrimary(productId, imageId)));
    }

    @DeleteMapping("/{imageId}")
    @Operation(summary = "Xóa ảnh sản phẩm",
               description = "Xóa record DB và xóa file trên Cloudinary.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {
        imageService.delete(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success("Xóa ảnh thành công"));
    }
}
