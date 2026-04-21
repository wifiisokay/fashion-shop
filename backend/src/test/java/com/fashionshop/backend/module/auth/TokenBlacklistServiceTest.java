package com.fashionshop.backend.module.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistServiceTest {

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService();
    }

    @Test
    void isBlacklisted_whenTokenNotAdded_returnsFalse() {
        assertThat(service.isBlacklisted("unknown-token")).isFalse();
    }

    @Test
    void isBlacklisted_afterBlacklist_returnsTrue() {
        Date future = new Date(System.currentTimeMillis() + 60_000);
        service.blacklist("my-token", future);

        assertThat(service.isBlacklisted("my-token")).isTrue();
    }

    @Test
    void isBlacklisted_whenTokenExpired_returnsFalseAndEagerCleans() {
        // Token đã expired ngay lập tức
        Date past = new Date(System.currentTimeMillis() - 1);
        service.blacklist("expired-token", past);

        // Nên trả false vì token đã hết hạn
        assertThat(service.isBlacklisted("expired-token")).isFalse();
        // Và double-check sau cleanup
        assertThat(service.isBlacklisted("expired-token")).isFalse();
    }

    @Test
    void isBlacklisted_differentTokensAreIndependent() {
        Date future = new Date(System.currentTimeMillis() + 60_000);
        Date past = new Date(System.currentTimeMillis() - 1);

        service.blacklist("valid-token", future);
        service.blacklist("expired-token", past);

        assertThat(service.isBlacklisted("valid-token")).isTrue();
        assertThat(service.isBlacklisted("expired-token")).isFalse();
        assertThat(service.isBlacklisted("never-added")).isFalse();
    }

    @Test
    void cleanup_removesExpiredTokens() {
        Date past = new Date(System.currentTimeMillis() - 1);
        Date future = new Date(System.currentTimeMillis() + 60_000);

        service.blacklist("expired-token", past);
        service.blacklist("valid-token", future);

        service.cleanup();

        // Sau cleanup: expired bị xóa, valid còn
        assertThat(service.isBlacklisted("expired-token")).isFalse();
        assertThat(service.isBlacklisted("valid-token")).isTrue();
    }

    @Test
    void blacklist_calledTwiceWithSameToken_lastWins() {
        Date expiry1 = new Date(System.currentTimeMillis() - 1); // expired
        Date expiry2 = new Date(System.currentTimeMillis() + 60_000); // valid

        service.blacklist("token", expiry1);
        service.blacklist("token", expiry2); // overwrite

        // Lần blacklist thứ 2 với expiry tương lai nên token vẫn còn valid
        assertThat(service.isBlacklisted("token")).isTrue();
    }
}
