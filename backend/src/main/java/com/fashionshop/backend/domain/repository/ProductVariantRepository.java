package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductId(Long productId);

    boolean existsByProductIdAndColorAndSize(Long productId, String color, String size);

    /** Check trùng khi update — loại trừ chính variant đang sửa. */
    boolean existsByProductIdAndColorAndSizeAndIdNot(Long productId, String color, String size, Long id);
}
