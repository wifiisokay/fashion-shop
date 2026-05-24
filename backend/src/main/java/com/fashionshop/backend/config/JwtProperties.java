package com.fashionshop.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe config binding cho jwt.* properties.
 * Giải quyết warnings "unknown property" trong application.properties.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long expiration = 86400000L;          // 24h
    private long refreshExpiration = 604800000L;  // 7 days

    private Cookie cookie = new Cookie();

    @Getter
    @Setter
    public static class Cookie {
        private String name = "access_token";
        private boolean httpOnly = true;
        private boolean secure = false;           // true khi production (HTTPS)
        private String sameSite = "Strict";
        private String path = "/api";
        private int maxAge = 86400;               // seconds (24h)
    }
}
