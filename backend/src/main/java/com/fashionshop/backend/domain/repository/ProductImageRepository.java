package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    List<ProductImage> findByColorIdOrderBySortOrderAsc(Long colorId);

    @Query("""
        SELECT pi FROM ProductImage pi
        WHERE pi.product.id = :productId
          AND pi.color.id = :colorId
          AND pi.isPrimary = true
        """)
    Optional<ProductImage> findColorThumbnail(Long productId, Long colorId);

    @Query(value = """
        SELECT pi.* FROM product_images pi
        JOIN product_colors pc ON pc.id = pi.color_id
        WHERE pi.product_id = :productId
          AND pi.color_id IS NOT NULL
          AND pi.is_primary = true
        ORDER BY pc.display_order ASC, pc.id ASC, pi.id ASC
        LIMIT 1
        """, nativeQuery = true)
    Optional<ProductImage> findPrimaryByProductId(Long productId);

    @Query("""
        SELECT pi FROM ProductImage pi
        WHERE pi.product.id = :productId
          AND pi.color IS NULL
          AND pi.isPrimary = false
        ORDER BY pi.sortOrder ASC, pi.id ASC
        """)
    List<ProductImage> findSharedGalleryByProductId(Long productId);

    @Modifying
    @Query("UPDATE ProductImage pi SET pi.isPrimary = false WHERE pi.product.id = :productId AND pi.color IS NULL")
    void clearPrimaryByProductId(Long productId);

    long countByColorId(Long colorId);

    @Query("SELECT COALESCE(MAX(pi.sortOrder), 0) FROM ProductImage pi WHERE pi.color.id = :colorId")
    int findMaxSortOrderByColorId(Long colorId);

    @Query("""
        SELECT COALESCE(MAX(pi.sortOrder), 0) FROM ProductImage pi
        WHERE pi.product.id = :productId
          AND pi.color IS NULL
          AND pi.isPrimary = false
        """)
    int findMaxSharedGallerySortOrder(Long productId);

    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id = :productId AND pi.color IS NULL AND pi.isPrimary = true")
    List<ProductImage> findAllPrimaryByProductId(Long productId);
}
