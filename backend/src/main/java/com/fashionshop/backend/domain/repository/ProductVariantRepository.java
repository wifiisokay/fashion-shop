package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductId(Long productId);

    List<ProductVariant> findByColorId(Long colorId);

    boolean existsByColorIdAndSize(Long colorId, String size);

    /** Check trùng khi update — loại trừ chính variant đang sửa. */
    boolean existsByColorIdAndSizeAndIdNot(Long colorId, String size, Long id);

    /** SELECT ... FOR UPDATE — chống race condition khi trừ tồn kho. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") Long id);
}

