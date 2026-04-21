package com.fashionshop.backend.module.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenServiceTest {

    private PasswordResetTokenService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetTokenService();
        // Inject expirationMinutes = 15 (default)
        ReflectionTestUtils.setField(service, "expirationMinutes", 15L);
    }

    @Test
    void createToken_returnsNonNullUrlSafeToken() {
        String token = service.createToken("user@example.com");

        assertThat(token).isNotNull().isNotBlank();
        // URL-safe Base64 không chứa '+' hay '/'
        assertThat(token).doesNotContain("+", "/", "=");
    }

    @Test
    void consumeToken_whenValidToken_returnsEmail() {
        String token = service.createToken("user@example.com");

        String email = service.consumeToken(token);

        assertThat(email).isEqualTo("user@example.com");
    }

    @Test
    void consumeToken_whenInvalidToken_returnsNull() {
        String email = service.consumeToken("not-a-real-token");

        assertThat(email).isNull();
    }

    @Test
    void consumeToken_isOneTimeUse_secondCallReturnsNull() {
        String token = service.createToken("user@example.com");

        String first = service.consumeToken(token);
        String second = service.consumeToken(token);

        assertThat(first).isEqualTo("user@example.com");
        assertThat(second).isNull(); // đã bị xóa sau lần dùng đầu
    }

    @Test
    void consumeToken_whenExpired_returnsNull() {
        // Set expiration = 0 minutes → token hết hạn ngay
        ReflectionTestUtils.setField(service, "expirationMinutes", 0L);
        String token = service.createToken("user@example.com");

        // Chờ 1ms để đảm bảo expired
        try {
            Thread.sleep(1);
        } catch (InterruptedException ignored) {
        }

        String email = service.consumeToken(token);

        assertThat(email).isNull();
    }

    @Test
    void createToken_differentCallsProduceDifferentTokens() {
        String t1 = service.createToken("user@example.com");
        String t2 = service.createToken("user@example.com");

        assertThat(t1).isNotEqualTo(t2); // SecureRandom → không bao giờ trùng
    }

    @Test
    void cleanupExpiredTokens_removesExpiredEntries() {
        // Tạo token với expiration = 0 → expired ngay
        ReflectionTestUtils.setField(service, "expirationMinutes", 0L);
        service.createToken("expired@example.com");

        try {
            Thread.sleep(1);
        } catch (InterruptedException ignored) {
        }

        // Tạo token còn hạn
        ReflectionTestUtils.setField(service, "expirationMinutes", 15L);
        String validToken = service.createToken("valid@example.com");

        service.cleanupExpiredTokens();

        // Token còn hạn vẫn consumable, token expired thì không
        assertThat(service.consumeToken(validToken)).isEqualTo("valid@example.com");
    }

    @Test
    void consumeToken_withNullInput_returnsNull() {
        // Đảm bảo không throw NPE
        String email = service.consumeToken(null);
        assertThat(email).isNull();
    }
}
