package com.fashionshop.backend.config;

import com.fashionshop.backend.module.auth.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * JWT Auth Filter — đọc token từ HttpOnly Cookie hoặc Authorization header (fallback).
 * Thứ tự xử lý:
 * 1. Đọc token từ HttpOnly cookie "access_token"
 * 2. Fallback: đọc từ Authorization: Bearer <token>
 * 3. Check blacklist (logout)
 * 4. Validate token
 * 5. Set SecurityContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Kiểm tra blacklist trước
            if (tokenBlacklistService.isBlacklisted(token)) {
                log.debug("Token is blacklisted");
                filterChain.doFilter(request, response);
                return;
            }

            // Validate cấu trúc token
            if (jwtService.isTokenStructureValid(token)) {
                String email = jwtService.extractEmail(token);
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    if (jwtService.isValid(token, userDetails)) {
                        var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                } catch (Exception e) {
                    log.debug("Failed to load user for token: {}", e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Ưu tiên HttpOnly cookie, fallback sang Authorization header.
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Cookie (bảo mật hơn — không accessible từ JS)
        String cookieName = jwtProperties.getCookie().getName();
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(extractFromHeader(request));
        }
        // 2. Authorization header fallback
        return extractFromHeader(request);
    }

    private String extractFromHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
