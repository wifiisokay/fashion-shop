package com.fashionshop.backend.module.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.fashionshop.backend.config.JwtProperties;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthCookieService {

    private final JwtProperties jwtProperties;

    public void setAuthCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token,
            Duration.ofMillis(jwtProperties.getExpiration())).toString());
    }

    public void clearAuthCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    public String getCookieName() {
        return jwtProperties.getCookie().getName();
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        JwtProperties.Cookie config = jwtProperties.getCookie();
        return ResponseCookie.from(config.getName(), value)
            .httpOnly(config.isHttpOnly())
            .secure(config.isSecure())
            .sameSite(config.getSameSite())
            .path(config.getPath())
            .maxAge(maxAge)
            .build();
    }
}
