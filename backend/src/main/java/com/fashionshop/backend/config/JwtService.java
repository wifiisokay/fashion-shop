package com.fashionshop.backend.config;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.fashionshop.backend.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT service — generate, validate, extract.
 * Token chứa claims: sub=email, userId, role.
 * Token được lưu trong HttpOnly Cookie (set bởi AuthController).
 *
 * signingKey được cache bằng @PostConstruct để tránh parse lại mỗi request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    /** Cache signing key — tính một lần khi bean khởi động. */
    private SecretKey cachedSigningKey;

    @PostConstruct
    void init() {
        this.cachedSigningKey = buildSigningKey();
        log.debug("JwtService initialized — signing key cached");
    }

    /**
     * Generate JWT access token. Gắn userId và role vào claims.
     */
    public String generateToken(User user) {
        return Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId())
            .claim("role", user.getRole().name())
            .id(UUID.randomUUID().toString())
            .claim("tokenVersion", user.getTokenVersion())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
            .signWith(cachedSigningKey)
            .compact();
    }

    /**
     * Extract email (subject) từ token.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract expiry date từ token claims.
     * Dùng để blacklist token khi logout — không cần phụ thuộc vào JwtProperties ở nơi khác.
     */
    public Date getExpiry(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> claims.get("tokenVersion", Integer.class));
    }

    public boolean shouldRenew(String token) {
        return getExpiry(token).getTime() - System.currentTimeMillis() < jwtProperties.getRenewThreshold();
    }

    /**
     * Validate: token chưa expired + khớp với UserDetails.
     */
    public boolean isValid(String token, UserDetails userDetails) {
        try {
            String email = extractEmail(token);
            if (!email.equals(userDetails.getUsername()) || isExpired(token)) {
                return false;
            }
            if (userDetails instanceof User user) {
                return user.isEnabled() && user.isAccountNonLocked()
                    && java.util.Objects.equals(extractTokenVersion(token), user.getTokenVersion());
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate chỉ signature + expiry (không cần UserDetails).
     * Dùng trong JwtAuthFilter trước khi load user.
     */
    public boolean isTokenStructureValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ============ Private helpers ============

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(getClaims(token));
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(cachedSigningKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey buildSigningKey() {
        String rawSecret = jwtProperties.getSecret();
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException("JWT secret is missing. Please set jwt.secret or JWT_SECRET.");
        }

        String secret = rawSecret.trim();

        // Prefer Base64 secret if provided; fallback to plain text for compatibility with existing setups.
        try {
            byte[] base64Key = Decoders.BASE64.decode(secret);
            if (base64Key.length >= 32) {
                return Keys.hmacShaKeyFor(base64Key);
            }
            log.warn("JWT secret appears Base64 but too short (<32 bytes). Falling back to plain-text secret bytes.");
        } catch (IllegalArgumentException | DecodingException ignored) {
            // Not standard Base64, try Base64URL format next.
        }

        try {
            byte[] base64UrlKey = Decoders.BASE64URL.decode(secret);
            if (base64UrlKey.length >= 32) {
                return Keys.hmacShaKeyFor(base64UrlKey);
            }
            log.warn("JWT secret appears Base64URL but too short (<32 bytes). Falling back to plain-text secret bytes.");
        } catch (IllegalArgumentException | DecodingException ignored) {
            // Not Base64URL either, continue with plain text secret.
        }

        byte[] plainKey = secret.getBytes(StandardCharsets.UTF_8);
        if (plainKey.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256.");
        }
        return Keys.hmacShaKeyFor(plainKey);
    }
}
