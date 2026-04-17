package com.fashionshop.backend.module.product;

import com.fashionshop.backend.module.product.dto.request.ProductVariantRequest;
import com.fashionshop.backend.module.product.dto.response.ProductVariantResponse;

import java.util.List;

public interface ProductVariantService {

    List<ProductVariantResponse> getByProductId(Long productId);

    ProductVariantResponse create(Long productId, ProductVariantRequest request);

    ProductVariantResponse update(Long productId, Long variantId, ProductVariantRequest request);

    void delete(Long productId, Long variantId);
}
