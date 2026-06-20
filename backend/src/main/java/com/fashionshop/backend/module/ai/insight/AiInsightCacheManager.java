package com.fashionshop.backend.module.ai.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.module.ai.insight.dto.AiInsightResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Cache Redis cho AI Insight.
 *
 * Pattern giống OutfitCacheManager:
 *   - StringRedisTemplate + ObjectMapper để serialize/deserialize JSON.
 *   - Best-effort: mọi lỗi Redis đều log warn và bỏ qua, không crash hệ thống.
 *
 * Key pattern: {prefix}:{productId}
 * TTL: 12 giờ (configurable)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiInsightCacheManager {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.insight-cache.ttl-hours:12}")
    private long cacheTtlHours;

    @Value("${ai.insight-cache.prefix:fashion-shop:ai:insight}")
    private String cacheKeyPrefix;

    // ──────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────

    public Optional<AiInsightResponse> tryLoad(Long productId) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey(productId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            AiInsightResponse response = objectMapper.readValue(json, AiInsightResponse.class);
            if (response == null) {
                return Optional.empty();
            }
            log.debug("[AI_INSIGHT] cache_hit productId={}", productId);
            return Optional.of(response);
        } catch (RedisConnectionFailureException e) {
            log.warn("[AI_INSIGHT] cache_read_unavailable productId={} error={}", productId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[AI_INSIGHT] cache_read_skipped productId={} error={}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    // ──────────────────────────────────────────────
    // Write
    // ──────────────────────────────────────────────

    public void save(Long productId, AiInsightResponse response) {
        if (response == null) return;
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey(productId), json, Duration.ofHours(cacheTtlHours));
            log.debug("[AI_INSIGHT] cache_saved productId={} ttlHours={}", productId, cacheTtlHours);
        } catch (RedisConnectionFailureException e) {
            log.warn("[AI_INSIGHT] cache_write_unavailable productId={} error={}", productId, e.getMessage());
        } catch (Exception e) {
            log.warn("[AI_INSIGHT] cache_write_skipped productId={} error={}", productId, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Evict — dùng khi có review mới được tạo
    // ──────────────────────────────────────────────

    public void evict(Long productId) {
        try {
            redisTemplate.delete(cacheKey(productId));
            log.debug("[AI_INSIGHT] cache_evicted productId={}", productId);
        } catch (Exception e) {
            log.warn("[AI_INSIGHT] cache_evict_failed productId={} error={}", productId, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────

    private String cacheKey(Long productId) {
        return cacheKeyPrefix + ":" + productId;
    }
}
