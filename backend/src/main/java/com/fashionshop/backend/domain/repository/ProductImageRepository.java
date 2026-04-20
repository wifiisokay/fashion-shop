package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    /** Ảnh theo màu — dùng cho gallery detail. */
    List<ProductImage> findByColorIdOrderBySortOrderAsc(Long colorId);

    /** Ảnh chung sản phẩm (color IS NULL, isPrimary=true) — dùng cho listing. */
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.color IS NULL AND pi.isPrimary = true")
    Optional<ProductImage> findPrimaryByProductId(Long productId);

    /** Clear isPrimary trên tất cả ảnh chung (color IS NULL) của product — dùng trước khi set primary mới. */
    @Modifying
    @Query("UPDATE ProductImage pi SET pi.isPrimary = false WHERE pi.product.id = :productId AND pi.color IS NULL")
    void clearPrimaryByProductId(Long productId);

    /** Đếm ảnh theo colorId — dùng để enforce giới hạn 5 ảnh/màu. */
    long countByColorId(Long colorId);

    /** Tìm max sort_order theo colorId — dùng để auto-increment khi upload ảnh gallery. */
    @Query("SELECT COALESCE(MAX(pi.sortOrder), 0) FROM ProductImage pi WHERE pi.color.id = :colorId")
    int findMaxSortOrderByColorId(Long colorId);

    /** Tìm tất cả ảnh primary (color IS NULL, isPrimary=true) — dùng để thay thế khi upload primary mới. */
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.color IS NULL AND pi.isPrimary = true")
    List<ProductImage> findAllPrimaryByProductId(Long productId);
}
