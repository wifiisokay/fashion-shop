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
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products/{productId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
@Tag(name = "Admin Product Images", description = "Upload/xoa anh san pham")
public class AdminProductImageController {

    private final ProductImageService imageService;

    @GetMapping
    @Operation(summary = "Danh sach anh cua san pham", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(imageService.getByProductId(productId)));
    }

    @PostMapping(value = "/colors/{colorId}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload thumbnail theo mau",
               description = "Upsert 1 thumbnail cho mau: color_id != null, is_primary=true.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductImageResponse>> uploadColorThumbnail(
        @PathVariable Long productId,
        @PathVariable Long colorId,
        @RequestParam("file") MultipartFile file
    ) {
        ProductImageResponse response = imageService.uploadColorThumbnail(productId, colorId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Upload thumbnail mau thanh cong", response));
    }

    @PostMapping(value = "/gallery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload anh gallery chung",
               description = "Them anh gallery chung: color_id=null, is_primary=false.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductImageResponse>> uploadGalleryImage(
        @PathVariable Long productId,
        @RequestParam("file") MultipartFile file
    ) {
        ProductImageResponse response = imageService.uploadGalleryImage(productId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Upload anh gallery thanh cong", response));
    }

    @PatchMapping("/{imageId}/reorder")
    @Operation(summary = "Doi thu tu anh gallery chung", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductImageResponse>> reorder(
        @PathVariable Long productId,
        @PathVariable Long imageId,
        @RequestBody Map<String, Integer> body
    ) {
        Integer newSortOrder = body.get("sortOrder");
        return ResponseEntity.ok(ApiResponse.success(
            "Doi thu tu thanh cong", imageService.reorder(productId, imageId, newSortOrder)));
    }

    @DeleteMapping("/{imageId}")
    @Operation(summary = "Xoa anh san pham", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {
        imageService.delete(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success("Xoa anh thanh cong"));
    }
}
