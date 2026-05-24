package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.product.dto.request.ProductRequest;
import com.fashionshop.backend.module.product.dto.request.ProductStatusRequest;
import com.fashionshop.backend.module.product.dto.response.ProductDetailResponse;
import com.fashionshop.backend.module.product.dto.response.ProductSummaryResponse;

public interface ProductService {

    // ===== Admin =====
    ProductDetailResponse create(ProductRequest request, User currentUser);
    ProductDetailResponse update(Long id, ProductRequest request);
    ProductDetailResponse updateStatus(Long id, ProductStatusRequest request);
    ProductDetailResponse getByIdAdmin(Long id);
    PageResponse<ProductSummaryResponse> listAdmin(String keyword, Integer categoryId, String status, String gender,
                                                    int page, int size);

    // ===== Public =====
    PageResponse<ProductSummaryResponse> listPublic(String keyword, Integer categoryId, String gender,
                                                    Boolean isSale, String color, String sizeOption,
                                                    java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
                                                    String sort, int page, int size);
    ProductDetailResponse getByIdPublic(Long id);
}
