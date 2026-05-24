package com.fashionshop.backend.module.product;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.module.product.dto.request.CategoryRequest;
import com.fashionshop.backend.module.product.dto.response.CategoryResponse;
import com.fashionshop.backend.module.product.dto.response.CategoryTreeResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * CategoryController — Public read + Admin CRUD.
 * GET /api/categories/** → public
 * POST/PUT/DELETE /api/admin/categories/** → ADMIN only
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Quản lý danh mục sản phẩm")
public class CategoryController {

    private final CategoryService categoryService;

    // ============ PUBLIC ============

    @GetMapping("/api/categories")
    @Operation(summary = "Lấy cây danh mục", description = "Trả về cây 2 cấp: mỗi danh mục cha chứa mảng children[].")
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getTree() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getTree()));
    }

    @GetMapping("/api/categories/{id}")
    @Operation(summary = "Lấy chi tiết danh mục")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getById(id)));
    }

    // ============ ADMIN ============

    @PostMapping("/api/admin/categories")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Tạo danh mục mới", security = @SecurityRequirement(name = "cookieAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Slug đã tồn tại")
    })
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
        @Valid @RequestBody CategoryRequest request
    ) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Tạo danh mục thành công", response));
    }

    @PutMapping("/api/admin/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Cập nhật danh mục", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
        @PathVariable Integer id,
        @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật danh mục thành công", categoryService.update(id, request)));
    }

    @DeleteMapping("/api/admin/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Xóa danh mục", description = "Chặn xóa nếu danh mục đang có sản phẩm.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa danh mục thành công"));
    }
}
