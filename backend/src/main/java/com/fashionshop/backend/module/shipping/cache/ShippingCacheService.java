package com.fashionshop.backend.module.shipping.cache;

import com.fashionshop.backend.module.shipping.GhnProperties;
import com.fashionshop.backend.module.shipping.dto.response.ShippingFeeResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * In-memory cache cho phí ship.
 * Key: "{districtCode}:{wardCode}" — fee chỉ phụ thuộc vị trí, không phụ thuộc user.
 * Dùng Caffeine: TTL 5 phút, max 500 entries.
 */
@Service
@RequiredArgsConstructor
public class ShippingCacheService {

    private final GhnProperties ghnProperties;
    private Cache<String, ShippingFeeResponse> cache;

    @PostConstruct
    void init() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(ghnProperties.getCache().getTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(ghnProperties.getCache().getMaxSize())
                .build();
    }

    public Optional<ShippingFeeResponse> get(String key) {
        ShippingFeeResponse value = cache.getIfPresent(key);
        return Optional.ofNullable(value);
    }

    public void put(String key, ShippingFeeResponse value) {
        cache.put(key, value);
    }

    /**
     * Build cache key từ district code và ward code.
     */
    public static String buildKey(int districtCode, String wardCode) {
        return districtCode + ":" + wardCode;
    }
}
