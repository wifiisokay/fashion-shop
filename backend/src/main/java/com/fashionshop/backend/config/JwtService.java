package com.fashionshop.backend.config;

import com.fashionshop.backend.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

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

    /**
     * Validate: token chưa expired + khớp với UserDetails.
     */
    public boolean isValid(String token, UserDetails userDetails) {
        try {
            String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isExpired(token);
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

        // Prefer Base64 secret if provided; fallback to plain text for compatibility with existing setups.
        try {
            byte[] base64Key = Decoders.BASE64.decode(rawSecret);
            if (base64Key.length >= 32) {
                return Keys.hmacShaKeyFor(base64Key);
            }
            log.warn("JWT secret appears Base64 but too short (<32 bytes). Falling back to plain-text secret bytes.");
        } catch (IllegalArgumentException ignored) {
            // Not a Base64 string, continue with plain text secret.
        }

        byte[] plainKey = rawSecret.getBytes(StandardCharsets.UTF_8);
        if (plainKey.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256.");
        }
        return Keys.hmacShaKeyFor(plainKey);
    }
}
