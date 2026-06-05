package com.fashionshop.backend.module.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.domain.repository.OutfitSuggestionCacheRepository;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutfitCacheManager {

    private static final int CACHE_TTL_HOURS = 6;

    private final OutfitSuggestionCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    public Optional<CachedOutfit> tryLoadValidCache(Long productId, Long colorId) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(CACHE_TTL_HOURS);
            return cacheRepository.findByProductIdAndNullableColorId(productId, colorId)
                .filter(cache -> cache.getCreatedAt() != null && cache.getCreatedAt().isAfter(cutoff))
                .map(cache -> new CachedOutfit(readCombos(cache.getSuggestions()), cache.getCreatedAt()))
                .filter(cache -> !cache.combos().isEmpty());
        } catch (Exception e) {
            log.warn("[OUTFIT] cache_read_skipped productId={}, colorId={}, error={}",
                productId, colorId, e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void safeUpsert(Long productId, Long colorId, List<OutfitComboResponse> combos) {
        String suggestions = writeCombos(combos);
        cacheRepository.upsertCache(productId, colorId, suggestions);
    }

    private List<OutfitComboResponse> readCombos(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<OutfitComboResponse>>() {});
        } catch (Exception e) {
            log.warn("Failed to read outfit cache: {}", e.getMessage());
            return List.of();
        }
    }

    private String writeCombos(List<OutfitComboResponse> combos) {
        try {
            return objectMapper.writeValueAsString(combos);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write outfit cache", e);
        }
    }

    public record CachedOutfit(List<OutfitComboResponse> combos, LocalDateTime createdAt) {
    }
}
