package com.fashionshop.backend.module.shipping;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the existing ghn.* keys from application.properties.
 */
@Component
@ConfigurationProperties(prefix = "ghn")
@Getter
@Setter
@Slf4j
public class GhnProperties {

    /** API token from GHN dashboard. Never log this value. */
    private String token;

    /** Shop ID from GHN dashboard. */
    private String shopId;

    /** Sandbox or production GHN base URL. */
    private String baseUrl;

    private Defaults defaults = new Defaults();
    private Fallback fallback = new Fallback();
    private Cache cache = new Cache();

    @PostConstruct
    void logRuntimeConfig() {
        log.info(
            "GHN runtime config: baseUrl={}, shopId={}, defaultPackage={}g/{}x{}x{}cm, fallbackFeeEnabled={}, fallbackFee={}",
            baseUrl,
            shopId,
            defaults.getWeight(),
            defaults.getLength(),
            defaults.getWidth(),
            defaults.getHeight(),
            false,
            fallback.getFee()
        );
    }

    @Getter
    @Setter
    public static class Defaults {
        private int weight;
        private int length;
        private int width;
        private int height;
    }

    @Getter
    @Setter
    public static class Fallback {
        private long fee;
        private int days;
    }

    @Getter
    @Setter
    public static class Cache {
        private int ttlMinutes;
        private int maxSize;
    }
}
