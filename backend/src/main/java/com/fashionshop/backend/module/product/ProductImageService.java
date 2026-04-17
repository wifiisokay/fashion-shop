package com.fashionshop.backend.module.product;

import com.fashionshop.backend.module.product.dto.response.ProductImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageService {

    List<ProductImageResponse> getByProductId(Long productId);

    ProductImageResponse upload(Long productId, MultipartFile file, Long variantId, Boolean isPrimary);

    ProductImageResponse setPrimary(Long productId, Long imageId);

    void delete(Long productId, Long imageId);
}
