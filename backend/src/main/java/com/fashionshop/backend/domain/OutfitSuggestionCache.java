package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Cache gợi ý outfit từ Gemini — TTL 24h.
 * Mỗi product chỉ có 1 cache entry (UNIQUE product_id).
 */
@Entity
@Table(name = "outfit_suggestion_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutfitSuggestionCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false, columnDefinition = "JSON")
    private String suggestions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
