package com.fashionshop.backend.module.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory reset token store (one-time use).
 * Luu hash(token) thay vi token goc.
 */
@Slf4j
@Service
public class PasswordResetTokenService {

    private final Map<String, TokenEntry> tokenStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${auth.reset-token-expiration-minutes:15}")
    private long expirationMinutes;

    public String createToken(String email) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant expiresAt = Instant.now().plusSeconds(expirationMinutes * 60);
        tokenStore.put(hash(rawToken), new TokenEntry(email, expiresAt));
        return rawToken;
    }

    public String consumeToken(String rawToken) {
        String key = hash(rawToken);
        TokenEntry entry = tokenStore.remove(key); // one-time use
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return entry.email();
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        int before = tokenStore.size();
        tokenStore.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        int removed = before - tokenStore.size();
        if (removed > 0) {
            log.debug("Password reset token cleanup removed {} expired tokens", removed);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record TokenEntry(String email, Instant expiresAt) {
    }
}
