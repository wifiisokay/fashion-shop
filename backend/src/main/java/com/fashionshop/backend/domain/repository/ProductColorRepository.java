package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ProductColor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductColorRepository extends JpaRepository<ProductColor, Long> {

    List<ProductColor> findByProductIdOrderByDisplayOrderAsc(Long productId);

    boolean existsByProductIdAndColorName(Long productId, String colorName);

    /** Check trùng khi update — loại trừ chính color đang sửa. */
    boolean existsByProductIdAndColorNameAndIdNot(Long productId, String colorName, Long id);
}
