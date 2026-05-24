package com.fashionshop.backend.domain.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fashionshop.backend.domain.OutfitSuggestionCache;

public interface OutfitSuggestionCacheRepository extends JpaRepository<OutfitSuggestionCache, Long> {

    Optional<OutfitSuggestionCache> findByProductId(Long productId);

    @Query("SELECT c FROM OutfitSuggestionCache c WHERE c.productId = :productId AND ((:colorId IS NULL AND c.colorId IS NULL) OR c.colorId = :colorId)")
    Optional<OutfitSuggestionCache> findByProductIdAndNullableColorId(@Param("productId") Long productId,
                                                                      @Param("colorId") Long colorId);

    @Modifying
    @Query(value = """
        INSERT INTO outfit_suggestion_cache (product_id, color_id, suggestions, created_at)
        VALUES (:productId, :colorId, :suggestions, NOW())
        ON DUPLICATE KEY UPDATE
            suggestions = VALUES(suggestions),
            created_at = VALUES(created_at)
        """, nativeQuery = true)
    void upsertCache(@Param("productId") Long productId,
                     @Param("colorId") Long colorId,
                     @Param("suggestions") String suggestions);

    /** Scheduler: xóa cache cũ hơn cutoff */
    @Modifying
    @Query("DELETE FROM OutfitSuggestionCache c WHERE c.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
