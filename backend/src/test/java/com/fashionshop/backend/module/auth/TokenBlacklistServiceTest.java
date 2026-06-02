package com.fashionshop.backend.module.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.domain.TokenBlacklist;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.TokenBlacklistRepository;
import com.fashionshop.backend.domain.repository.UserRepository;

class TokenBlacklistServiceTest {

    private TokenBlacklistRepository repository;
    private UserRepository userRepository;
    private JwtService jwtService;
    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        repository = mock(TokenBlacklistRepository.class);
        userRepository = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        service = new TokenBlacklistService(repository, userRepository, jwtService);
    }

    @Test
    void isBlacklisted_delegatesToRepositoryByJti() {
        when(repository.existsByJti("jti-1")).thenReturn(true);

        assertThat(service.isBlacklisted("jti-1")).isTrue();
        assertThat(service.isBlacklisted(null)).isFalse();
    }

    @Test
    void blacklistToken_savesJtiInsteadOfRawJwt() {
        User user = User.builder().id(7L).build();
        when(jwtService.extractJti("raw.jwt.token")).thenReturn("jti-7");
        when(jwtService.extractUserId("raw.jwt.token")).thenReturn(7L);
        when(jwtService.getExpiry("raw.jwt.token")).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.blacklistToken("raw.jwt.token", "LOGOUT");

        ArgumentCaptor<TokenBlacklist> captor = ArgumentCaptor.forClass(TokenBlacklist.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getJti()).isEqualTo("jti-7");
        assertThat(captor.getValue().getReason()).isEqualTo("LOGOUT");
        assertThat(captor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void cleanupExpiredTokens_deletesExpiredRows() {
        service.cleanupExpiredTokens();

        verify(repository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}
