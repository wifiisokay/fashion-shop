package com.fashionshop.backend.module.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutfitCacheManager {

    private static final String CACHE_KEY_PREFIX = "fashion-shop:ai:outfit:";
    private static final String LOCK_KEY_PREFIX = "fashion-shop:ai:outfit-lock:";
    private static final String NULL_COLOR = "none";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.outfit-cache.ttl-hours:6}")
    private long cacheTtlHours;

    @Value("${ai.outfit-cache.lock-ttl-seconds:60}")
    private long lockTtlSeconds;

    public Optional<CachedOutfit> tryLoadValidCache(Long productId, Long colorId) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey(productId, colorId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            CachedOutfitPayload payload = objectMapper.readValue(json, new TypeReference<>() {});
            if (payload == null || payload.combos() == null || payload.combos().isEmpty()) {
                return Optional.empty();
            }
            LocalDateTime createdAt = payload.createdAt() != null ? payload.createdAt() : LocalDateTime.now();
            return Optional.of(new CachedOutfit(payload.combos(), createdAt));
        } catch (Exception e) {
            log.warn("[OUTFIT] cache_read_skipped productId={}, colorId={}, error={}",
                productId, colorId, e.getMessage());
            return Optional.empty();
        }
    }

    public void safeUpsert(Long productId, Long colorId, List<OutfitComboResponse> combos) {
        if (combos == null || combos.isEmpty()) {
            return;
        }
        try {
            CachedOutfitPayload payload = new CachedOutfitPayload(LocalDateTime.now(), combos);
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(cacheKey(productId, colorId), json, Duration.ofHours(cacheTtlHours));
        } catch (Exception e) {
            log.warn("[OUTFIT] cache_write_skipped productId={}, colorId={}, error={}",
                productId, colorId, e.getMessage());
        }
    }

    public Optional<String> tryAcquireBuildLock(Long productId, Long colorId) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(productId, colorId), token, Duration.ofSeconds(lockTtlSeconds));
            return Boolean.TRUE.equals(locked) ? Optional.of(token) : Optional.empty();
        } catch (RedisConnectionFailureException e) {
            log.warn("[OUTFIT] cache_lock_unavailable productId={}, colorId={}, error={}",
                productId, colorId, e.getMessage());
            return Optional.of(token);
        } catch (Exception e) {
            log.warn("[OUTFIT] cache_lock_skipped productId={}, colorId={}, error={}",
                productId, colorId, e.getMessage());
            return Optional.of(token);
        }
    }

    public void releaseBuildLock(Long productId, Long colorId, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            String key = lockKey(productId, colorId);
            String currentToken = redisTemplate.opsForValue().get(key);
            if (token.equals(currentToken)) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.warn("[OUTFIT] cache_lock_release_skipped productId={}, colorId={}, error={}",
                productId, colorId, e.getMessage());
        }
    }

    private String cacheKey(Long productId, Long colorId) {
        return CACHE_KEY_PREFIX + productId + ":" + normalizeColorId(colorId);
    }

    private String lockKey(Long productId, Long colorId) {
        return LOCK_KEY_PREFIX + productId + ":" + normalizeColorId(colorId);
    }

    private String normalizeColorId(Long colorId) {
        return colorId == null ? NULL_COLOR : colorId.toString();
    }

    public record CachedOutfit(List<OutfitComboResponse> combos, LocalDateTime createdAt) {
    }

    private record CachedOutfitPayload(LocalDateTime createdAt, List<OutfitComboResponse> combos) {
    }
}
