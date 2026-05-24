package com.fashionshop.backend.config;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;

    /** Secret >= 32 bytes để HS256 không throw */
    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-32-bytes!!";

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_SECRET);
        jwtProperties.setExpiration(3_600_000L); // 1 giờ

        jwtService = new JwtService(jwtProperties);
        jwtService.init(); // trigger @PostConstruct manually
    }

    private User activeUser(String email) {
        return User.builder()
            .id(1L)
            .email(email)
            .fullName("Test User")
            .password("encoded")
            .role(Role.CUSTOMER)
            .status(UserStatus.ACTIVE)
            .build();
    }

    @Test
    void generateToken_returnsNonNullToken() {
        User user = activeUser("user@example.com");
        String token = jwtService.generateToken(user);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void extractEmail_returnsCorrectEmail() {
        User user = activeUser("user@example.com");
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void isValid_withMatchingUser_returnsTrue() {
        User user = activeUser("user@example.com");
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isValid(token, user)).isTrue();
    }

    @Test
    void isValid_withWrongUsername_returnsFalse() {
        User user    = activeUser("user@example.com");
        User another = activeUser("other@example.com");
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isValid(token, another)).isFalse();
    }

    @Test
    void isValid_withMalformedToken_returnsFalse() {
        User user = activeUser("user@example.com");
        assertThat(jwtService.isValid("not.a.jwt", user)).isFalse();
    }

    @Test
    void isTokenStructureValid_withValidToken_returnsTrue() {
        User user  = activeUser("user@example.com");
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isTokenStructureValid(token)).isTrue();
    }

    @Test
    void isTokenStructureValid_withGarbage_returnsFalse() {
        assertThat(jwtService.isTokenStructureValid("garbage")).isFalse();
        assertThat(jwtService.isTokenStructureValid("a.b.c")).isFalse();
    }

    @Test
    void getExpiry_returnsDateInFuture() {
        User user = activeUser("user@example.com");
        String token = jwtService.generateToken(user);
        Date expiry = jwtService.getExpiry(token);

        assertThat(expiry).isAfter(new Date());
    }

    @Test
    void getExpiry_expiryMatchesConfiguredExpiration() {
        User user = activeUser("user@example.com");
        long before = System.currentTimeMillis();
        String token = jwtService.generateToken(user);
        Date expiry = jwtService.getExpiry(token);

        long expectedMs = before + jwtProperties.getExpiration();
        // Tolerância de 5s para execução do teste
        assertThat(expiry.getTime()).isBetween(expectedMs - 5_000, expectedMs + 5_000);
    }

    @Test
    void init_throwsIfSecretMissing() {
        JwtProperties emptyProps = new JwtProperties();
        emptyProps.setSecret(null);

        JwtService service = new JwtService(emptyProps);

        assertThatThrownBy(service::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT secret is missing");
    }

    @Test
    void init_throwsIfSecretTooShort() {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret("short"); // < 32 bytes, also not valid Base64

        JwtService service = new JwtService(shortProps);

        assertThatThrownBy(service::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void generateToken_withDifferentUsers_produceDifferentTokens() {
        User user1 = activeUser("a@example.com");
        User user2 = activeUser("b@example.com");

        String t1 = jwtService.generateToken(user1);
        String t2 = jwtService.generateToken(user2);

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void isValid_withNullUserDetails_returnsFalse() {
        User user = activeUser("user@example.com");
        String token = jwtService.generateToken(user);

        // Tạo UserDetails giả với username khác
        UserDetails fakeUser = mock(UserDetails.class);
        org.mockito.Mockito.when(fakeUser.getUsername()).thenReturn(null);

        assertThat(jwtService.isValid(token, fakeUser)).isFalse();
    }
}
