package com.fashionshop.backend.module.product;

import com.fashionshop.backend.module.product.dto.response.ProductImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageService {

    List<ProductImageResponse> getByProductId(Long productId);

    ProductImageResponse uploadColorThumbnail(Long productId, Long colorId, MultipartFile file);

    ProductImageResponse uploadGalleryImage(Long productId, MultipartFile file);

    ProductImageResponse reorder(Long productId, Long imageId, Integer newSortOrder);

    void delete(Long productId, Long imageId);
}
