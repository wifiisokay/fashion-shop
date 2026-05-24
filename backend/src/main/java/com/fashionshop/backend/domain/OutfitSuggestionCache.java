package com.fashionshop.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Cache gợi ý outfit từ Gemini — TTL 24h.
 * Unique theo (product_id, color_key) để tách theo màu.
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

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "color_id")
    private Long colorId;

    @Column(name = "color_key", insertable = false, updatable = false)
    private Long colorKey;

    @Column(nullable = false, columnDefinition = "JSON")
    private String suggestions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
