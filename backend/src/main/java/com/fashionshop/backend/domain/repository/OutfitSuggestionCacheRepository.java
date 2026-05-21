package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.OutfitSuggestionCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OutfitSuggestionCacheRepository extends JpaRepository<OutfitSuggestionCache, Long> {

    Optional<OutfitSuggestionCache> findByProductId(Long productId);

    @Query("SELECT c FROM OutfitSuggestionCache c WHERE c.productId = :productId AND ((:colorId IS NULL AND c.colorId IS NULL) OR c.colorId = :colorId)")
    Optional<OutfitSuggestionCache> findByProductIdAndNullableColorId(@Param("productId") Long productId,
                                                                      @Param("colorId") Long colorId);

    /** Scheduler: xóa cache cũ hơn cutoff */
    @Modifying
    @Query("DELETE FROM OutfitSuggestionCache c WHERE c.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
