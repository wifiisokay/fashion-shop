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
@Tag(name = "Admin Product Images", description = "Upload/xóa ảnh sản phẩm")
public class AdminProductImageController {

    private final ProductImageService imageService;

    @GetMapping
    @Operation(summary = "Danh sách ảnh của sản phẩm", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(imageService.getByProductId(productId)));
    }

    @PostMapping(value = "/primary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload ảnh thẻ sản phẩm",
               description = "Upload ảnh chính (listing). Tự thay thế nếu đã tồn tại — xóa ảnh cũ trên Cloudinary.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductImageResponse>> uploadPrimary(
        @PathVariable Long productId,
        @RequestParam("file") MultipartFile file
    ) {
        ProductImageResponse response = imageService.uploadPrimary(productId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Upload ảnh thẻ thành công", response));
    }

    @PostMapping(value = "/color/{colorId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload ảnh gallery theo màu",
               description = "Upload ảnh cho màu cụ thể. Giới hạn 5 ảnh/màu, sort_order tự tính.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductImageResponse>> uploadColorImage(
        @PathVariable Long productId,
        @PathVariable Long colorId,
        @RequestParam("file") MultipartFile file
    ) {
        ProductImageResponse response = imageService.uploadColorImage(productId, colorId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Upload ảnh màu thành công", response));
    }

    @PatchMapping("/{imageId}/reorder")
    @Operation(summary = "Đổi thứ tự ảnh", description = "Đổi sort_order của ảnh gallery theo màu.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductImageResponse>> reorder(
        @PathVariable Long productId,
        @PathVariable Long imageId,
        @RequestBody Map<String, Integer> body
    ) {
        Integer newSortOrder = body.get("sortOrder");
        return ResponseEntity.ok(ApiResponse.success(
            "Đổi thứ tự thành công", imageService.reorder(productId, imageId, newSortOrder)));
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
