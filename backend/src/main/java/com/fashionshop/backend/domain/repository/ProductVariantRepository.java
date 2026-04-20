package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductId(Long productId);

    List<ProductVariant> findByColorId(Long colorId);

    boolean existsByColorIdAndSize(Long colorId, String size);

    /** Check trùng khi update — loại trừ chính variant đang sửa. */
    boolean existsByColorIdAndSizeAndIdNot(Long colorId, String size, Long id);
}
