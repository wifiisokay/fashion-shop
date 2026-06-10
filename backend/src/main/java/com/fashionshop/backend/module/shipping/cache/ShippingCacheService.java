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
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    public void put(String key, ShippingFeeResponse value) {
        cache.put(key, value);
    }

    public static String buildKey(int districtCode, String wardCode, int weight, int length, int width, int height,
                                  int serviceId) {
        return districtCode + ":" + wardCode + ":" + weight + ":" + length + ":" + width + ":" + height + ":"
                + serviceId;
    }
}
