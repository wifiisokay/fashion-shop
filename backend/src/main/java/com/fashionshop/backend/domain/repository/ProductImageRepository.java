package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    List<ProductImage> findByVariantId(Long variantId);

    /** Clear isPrimary trên tất cả ảnh của product — dùng trước khi set primary mới. */
    @Modifying
    @Query("UPDATE ProductImage pi SET pi.isPrimary = false WHERE pi.product.id = :productId")
    void clearPrimaryByProductId(Long productId);
}
