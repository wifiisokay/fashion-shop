package com.fashionshop.backend.module.product;

import com.fashionshop.backend.module.product.dto.response.ProductImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageService {

    List<ProductImageResponse> getByProductId(Long productId);

    /** Upload ảnh thẻ sản phẩm (color=null, isPrimary=true). Tự thay thế nếu đã có. */
    ProductImageResponse uploadPrimary(Long productId, MultipartFile file);

    /** Upload ảnh gallery theo màu. Giới hạn 5 ảnh/màu, sort_order tự tính. */
    ProductImageResponse uploadColorImage(Long productId, Long colorId, MultipartFile file);

    /** Đổi sort_order của ảnh trong cùng màu. */
    ProductImageResponse reorder(Long productId, Long imageId, Integer newSortOrder);

    void delete(Long productId, Long imageId);
}
