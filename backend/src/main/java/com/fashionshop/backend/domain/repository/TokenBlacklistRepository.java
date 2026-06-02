package com.fashionshop.backend.domain.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fashionshop.backend.domain.TokenBlacklist;

public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

    boolean existsByJti(String jti);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
