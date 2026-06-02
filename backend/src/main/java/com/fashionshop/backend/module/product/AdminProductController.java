package com.fashionshop.backend.module.product;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.product.dto.request.ProductRequest;
import com.fashionshop.backend.module.product.dto.request.ProductStatusRequest;
import com.fashionshop.backend.module.product.dto.request.ProductTagSuggestRequest;
import com.fashionshop.backend.module.product.dto.request.UpdateProductSaleRequest;
import com.fashionshop.backend.module.product.dto.response.ProductDetailResponse;
import com.fashionshop.backend.module.product.dto.response.ProductSummaryResponse;
import com.fashionshop.backend.module.product.dto.response.StockAlertResponse;
import com.fashionshop.backend.module.product.dto.response.ProductTagLibraryResponse;
import com.fashionshop.backend.module.product.dto.response.ProductTagSuggestResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@Tag(name = "Admin Products", description = "CRUD sản phẩm cho Admin/Employee")
public class AdminProductController {

    private final ProductService productService;
    private final StockAlertService stockAlertService;
    private final ProductTagSuggestionService tagSuggestionService;
    private final ColorFamilyBackfillService colorFamilyBackfillService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo sản phẩm mới", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductDetailResponse>> create(
        @Valid @RequestBody ProductRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        ProductDetailResponse response = productService.create(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Tạo sản phẩm thành công", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật sản phẩm", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductDetailResponse>> update(
        @PathVariable Long id,
        @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật sản phẩm thành công", productService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Đổi trạng thái sản phẩm (ACTIVE/INACTIVE)", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody ProductStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cập nhật trạng thái thành công", productService.updateStatus(id, request)));
    }

    @PatchMapping("/{id}/sale")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cáº­p nháº­t giáº£m giÃ¡ sáº£n pháº©m", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateSale(
        @PathVariable Long id,
        @RequestBody UpdateProductSaleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            "Cáº­p nháº­t giáº£m giÃ¡ thÃ nh cÃ´ng", productService.updateSale(id, request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    @Operation(summary = "Danh sách sản phẩm (Admin)", description = "Hiển thị cả ACTIVE và INACTIVE.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer categoryId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String gender,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            productService.listAdmin(keyword, categoryId, status, gender, page, size)));
    }

    @GetMapping("/stock-alerts")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    @Operation(summary = "Cáº£nh bÃ¡o tá»“n kho theo biáº¿n thá»ƒ", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<StockAlertResponse>> stockAlerts(
        @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(stockAlertService.getStockAlerts(limit)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    @Operation(summary = "Chi tiết sản phẩm (Admin)", security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getByIdAdmin(id)));
    }

    @GetMapping("/tag-library")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    @Operation(summary = "Danh sách tag chuẩn hóa",
               description = "Trả toàn bộ tag vocabulary để FE build UI. staleTime=Infinity — chỉ cập nhật khi có version mới.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductTagLibraryResponse>> getTagLibrary() {
        return ResponseEntity.ok(ApiResponse.success(ProductTagLibraryResponse.fromLibrary()));
    }

    @PostMapping("/suggest-tags")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Gemini gợi ý tag cho sản phẩm",
               description = "Gọi Gemini phân tích tên + mô tả sản phẩm và trả tag gợi ý. Fallback trả empty nếu AI lỗi.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ProductTagSuggestResponse>> suggestTags(
            @Valid @RequestBody ProductTagSuggestRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Gợi ý tag thành công", tagSuggestionService.suggestTags(request)));
    }
    @PostMapping("/maintenance/backfill-color-family")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Backfill color_family cho product colors",
               description = "Derive color_family từ color_code cho từng product color.",
               security = @SecurityRequirement(name = "cookieAuth"))
    public ResponseEntity<ApiResponse<ColorFamilyBackfillService.Result>> backfillColorFamily() {
        return ResponseEntity.ok(ApiResponse.success(
                "Backfill color family thành công", colorFamilyBackfillService.run()));
    }
}
