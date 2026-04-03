package com.fashionshop.backend.config;

import com.fashionshop.backend.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Value("${security.rate-limit.login.max-requests:10}")
    private int loginMaxRequests;

    @Value("${security.rate-limit.login.window-seconds:60}")
    private int loginWindowSeconds;

    @Value("${security.rate-limit.forgot-password.max-requests:5}")
    private int forgotPasswordMaxRequests;

    @Value("${security.rate-limit.forgot-password.window-seconds:900}")
    private int forgotPasswordWindowSeconds;

    @Value("${security.rate-limit.reset-password.max-requests:10}")
    private int resetPasswordMaxRequests;

    @Value("${security.rate-limit.reset-password.window-seconds:900}")
    private int resetPasswordWindowSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = extractClientIp(request);

        if ("POST".equalsIgnoreCase(method)) {
            if ("/api/auth/login".equals(path)) {
                if (!allow("login", clientIp, loginMaxRequests, loginWindowSeconds)) {
                    writeRateLimitResponse(response);
                    return;
                }
            } else if ("/api/auth/forgot-password".equals(path)) {
                if (!allow("forgot-password", clientIp, forgotPasswordMaxRequests, forgotPasswordWindowSeconds)) {
                    writeRateLimitResponse(response);
                    return;
                }
            } else if ("/api/auth/reset-password".equals(path)) {
                if (!allow("reset-password", clientIp, resetPasswordMaxRequests, resetPasswordWindowSeconds)) {
                    writeRateLimitResponse(response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean allow(String endpoint, String clientIp, int maxRequests, int windowSeconds) {
        String key = endpoint + ":" + clientIp;
        long windowMillis = windowSeconds * 1000L;
        return rateLimitService.isAllowed(key, maxRequests, windowMillis);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ErrorCode.RATE_LIMIT_EXCEEDED.getDefaultMessage());
        body.put("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
        body.put("timestamp", Instant.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
