package com.fashionshop.backend.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.module.auth.AuthCookieService;
import com.fashionshop.backend.module.auth.TokenBlacklistService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class JwtAuthFilterTest {

    private JwtService jwtService;
    private UserRepository userRepository;
    private TokenBlacklistService tokenBlacklistService;
    private AuthCookieService authCookieService;
    private JwtAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userRepository = mock(UserRepository.class);
        tokenBlacklistService = mock(TokenBlacklistService.class);
        authCookieService = mock(AuthCookieService.class);
        JwtProperties properties = new JwtProperties();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        filter = new JwtAuthFilter(jwtService, properties, userRepository, tokenBlacklistService, authCookieService);
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validNearExpiryToken_authenticatesAndRenewsCookie() throws Exception {
        User user = activeUser();
        when(jwtService.isTokenStructureValid("token")).thenReturn(true);
        when(jwtService.extractJti("token")).thenReturn("jti-1");
        when(jwtService.extractUserId("token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(jwtService.isValid("token", user)).thenReturn(true);
        when(jwtService.shouldRenew("token")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("renewed-token");

        filter.doFilterInternal(request, response, chain);

        verify(authCookieService).setAuthCookie(response, "renewed-token");
        verify(chain).doFilter(request, response);
    }

    @Test
    void blacklistedJti_doesNotLoadUserOrRenew() throws Exception {
        when(jwtService.isTokenStructureValid("token")).thenReturn(true);
        when(jwtService.extractJti("token")).thenReturn("jti-1");
        when(tokenBlacklistService.isBlacklisted("jti-1")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(authCookieService, never()).setAuthCookie(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(chain).doFilter(request, response);
    }

    private User activeUser() {
        return User.builder()
            .id(1L)
            .email("user@example.com")
            .password("encoded")
            .role(Role.CUSTOMER)
            .status(UserStatus.ACTIVE)
            .tokenVersion(0)
            .build();
    }
}
