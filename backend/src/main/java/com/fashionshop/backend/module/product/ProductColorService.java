package com.fashionshop.backend.module.product;

import com.fashionshop.backend.module.product.dto.request.ProductColorRequest;
import com.fashionshop.backend.module.product.dto.response.ProductColorResponse;

import java.util.List;

public interface ProductColorService {
    List<ProductColorResponse> getByProductId(Long productId);
    ProductColorResponse create(Long productId, ProductColorRequest request);
    ProductColorResponse update(Long productId, Long colorId, ProductColorRequest request);
    void delete(Long productId, Long colorId);
}
