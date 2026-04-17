package com.fashionshop.backend.module.product;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.module.product.dto.response.ProductDetailResponse;
import com.fashionshop.backend.module.product.dto.response.ProductSummaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Public Product APIs — listing + detail.
 * Chỉ trả sản phẩm ACTIVE có variant còn hàng.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "API sản phẩm công khai")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Danh sách sản phẩm",
               description = "Lọc theo keyword, categoryId (bao gồm danh mục con), gender, isSale, color, sizeOption, minPrice, maxPrice. Chỉ ACTIVE + còn hàng.")
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer categoryId,
        @RequestParam(required = false) String gender,
        @RequestParam(required = false) Boolean isSale,
        @RequestParam(required = false) String color,
        @RequestParam(required = false) String sizeOption,
        @RequestParam(required = false) java.math.BigDecimal minPrice,
        @RequestParam(required = false) java.math.BigDecimal maxPrice,
        @RequestParam(defaultValue = "newest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            productService.listPublic(keyword, categoryId, gender, isSale, color, sizeOption, minPrice, maxPrice, sort, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết sản phẩm", description = "Trả đầy đủ variant + ảnh. Chỉ trả ACTIVE.")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getByIdPublic(id)));
    }
}
