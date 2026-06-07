package com.fashionshop.backend.module.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionshop.backend.module.ai.dto.response.OutfitComboResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutfitCacheManagerTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private OutfitCacheManager manager;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        manager = new OutfitCacheManager(redisTemplate, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(manager, "cacheTtlHours", 6L);
        ReflectionTestUtils.setField(manager, "lockTtlSeconds", 60L);
        ReflectionTestUtils.setField(manager, "cacheKeyPrefix", "fashion-shop:ai:outfit");
    }

    @Test
    void safeUpsertWritesRedisValueWithConfiguredTtl() {
        List<OutfitComboResponse> combos = List.of(combo("RULE"));

        manager.safeUpsert(10L, 20L, combos);

        verify(valueOperations).set(
            eq("fashion-shop:ai:outfit:10:20"),
            anyString(),
            eq(Duration.ofHours(6))
        );
    }

    @Test
    void tryLoadValidCacheReturnsCachedCombosFromRedis() {
        List<OutfitComboResponse> combos = List.of(combo("RULE"));
        manager.safeUpsert(10L, null, combos);
        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("fashion-shop:ai:outfit:10:none"), jsonCaptor.capture(), eq(Duration.ofHours(6)));
        when(valueOperations.get("fashion-shop:ai:outfit:10:none")).thenReturn(jsonCaptor.getValue());

        Optional<OutfitCacheManager.CachedOutfit> cached = manager.tryLoadValidCache(10L, null);

        assertThat(cached).isPresent();
        assertThat(cached.get().combos()).hasSize(1);
        assertThat(cached.get().combos().get(0).getProvider()).isEqualTo("RULE");
        assertThat(cached.get().createdAt()).isNotNull();
    }

    @Test
    void tryLoadValidCacheReturnsEmptyWhenRedisFails() {
        when(valueOperations.get("fashion-shop:ai:outfit:10:20"))
            .thenThrow(new RedisConnectionFailureException("down"));

        Optional<OutfitCacheManager.CachedOutfit> cached = manager.tryLoadValidCache(10L, 20L);

        assertThat(cached).isEmpty();
    }

    @Test
    void tryAcquireBuildLockReturnsEmptyWhenLockAlreadyExists() {
        when(valueOperations.setIfAbsent(eq("fashion-shop:ai:outfit-lock:10:20"), anyString(), eq(Duration.ofSeconds(60))))
            .thenReturn(false);

        Optional<String> token = manager.tryAcquireBuildLock(10L, 20L);

        assertThat(token).isEmpty();
    }

    private OutfitComboResponse combo(String provider) {
        return OutfitComboResponse.builder()
            .provider(provider)
            .style("daily")
            .label("Daily")
            .build();
    }
}
