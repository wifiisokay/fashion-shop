package com.fashionshop.backend.module.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.domain.TokenBlacklist;
import com.fashionshop.backend.domain.repository.TokenBlacklistRepository;
import com.fashionshop.backend.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional
    public void blacklistToken(String token, String reason) {
        String jti = jwtService.extractJti(token);
        if (jti == null || jti.isBlank() || tokenBlacklistRepository.existsByJti(jti)) {
            return;
        }
        Long userId = jwtService.extractUserId(token);
        TokenBlacklist entry = TokenBlacklist.builder()
            .jti(jti)
            .user(userId != null ? userRepository.findById(userId).orElse(null) : null)
            .expiresAt(jwtService.getExpiry(token).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            .reason(reason)
            .createdAt(LocalDateTime.now())
            .build();
        try {
            tokenBlacklistRepository.save(entry);
        } catch (DataIntegrityViolationException ignored) {
            log.debug("JWT jti already blacklisted: {}", jti);
        }
    }

    public boolean isBlacklisted(String jti) {
        return jti != null && !jti.isBlank() && tokenBlacklistRepository.existsByJti(jti);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        tokenBlacklistRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
