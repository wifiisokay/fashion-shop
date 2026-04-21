package com.fashionshop.backend.module.auth;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.auth.dto.request.ChangePasswordRequest;
import com.fashionshop.backend.module.auth.dto.request.ForgotPasswordRequest;
import com.fashionshop.backend.module.auth.dto.request.LoginRequest;
import com.fashionshop.backend.module.auth.dto.request.RegisterRequest;
import com.fashionshop.backend.module.auth.dto.request.ResetPasswordRequest;
import com.fashionshop.backend.module.auth.dto.response.AuthResponse;
import com.fashionshop.backend.module.auth.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;

/**
 * Auth Controller — xử lý đăng ký, đăng nhập, logout, đổi mật khẩu.
 *
 * BẢO MẬT — HttpOnly Cookie design:
 * - Token KHÔNG được trả trong body
 * - Sau login/register: token được set vào HttpOnly cookie "access_token"
 * - JS phía frontend không thể đọc cookie này → bảo vệ khỏi XSS
 * - Cookie tự động gửi kèm request → không cần lưu token trong localStorage
 * - Logout: clear cookie bằng cách gửi cookie rỗng MaxAge=0
 *
 * Kiến trúc: Controller chỉ điều phối HTTP layer — mọi logic nghiệp vụ
 * (kể cả generate token) đều nằm trong AuthService.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Cookie JWT authentication APIs. Access token is stored in HttpOnly cookie 'access_token'.")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    // Cookie configuration constants — đồng bộ với JwtProperties nhưng không inject thêm bean
    private static final String COOKIE_NAME = "access_token";
    private static final String COOKIE_PATH = "/api";
    private static final String SAME_SITE   = "Strict";

    /**
     * POST /api/auth/register — Public
     * Tạo tài khoản, set HttpOnly cookie, trả về thông tin user.
     */
    @PostMapping("/register")
    @Operation(summary = "Register new account", description = "Create customer account and set HttpOnly cookie access_token. Token is not returned in response body.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Register successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":false,\"message\":\"email: Email không đúng định dạng\",\"errorCode\":\"VALIDATION_001\",\"timestamp\":\"2026-03-31T15:00:00Z\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already exists", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Email đã được sử dụng\",\"errorCode\":\"AUTH_002\",\"timestamp\":\"2026-03-31T15:00:00Z\"}")))
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        LoginResult result = authService.register(request);
        setAuthCookie(response, result.token());
        return ResponseEntity.ok(ApiResponse.success("Đăng ký thành công", result.userInfo()));
    }

    /**
     * POST /api/auth/login — Public
     * Đăng nhập, set HttpOnly cookie, trả về thông tin user.
     */
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate by email/password and set HttpOnly cookie access_token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Email hoặc mật khẩu không đúng\",\"errorCode\":\"AUTH_003\",\"timestamp\":\"2026-03-31T15:00:00Z\"}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account locked", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Tài khoản đã bị khóa\",\"errorCode\":\"AUTH_004\",\"timestamp\":\"2026-03-31T15:00:00Z\"}")))
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        LoginResult result = authService.login(request);
        setAuthCookie(response, result.token());
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", result.userInfo()));
    }

    /**
     * POST /api/auth/forgot-password — Public
     * Sinh reset token một lần dùng và gửi qua kênh out-of-band (email/log).
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Generate one-time reset token. Always returns success message to avoid revealing whether email exists.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Nếu email tồn tại, hệ thống đã gửi hướng dẫn đặt lại mật khẩu"));
    }

    /**
     * POST /api/auth/reset-password — Public
     * Đặt lại mật khẩu bằng reset token.
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Reset password using one-time token from forgot-password flow.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid token or validation error")
    })
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công"));
    }

    /**
     * GET /api/auth/me — Authenticated
     * Lấy thông tin user hiện tại từ token trong cookie.
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Read authenticated user info from JWT token in HttpOnly cookie (or Bearer token fallback).", security = {
            @SecurityRequirement(name = "cookieAuth"),
            @SecurityRequirement(name = "bearerAuth")
    })
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current user returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated or invalid token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse<UserResponse>> me(
            @AuthenticationPrincipal(expression = "username") String currentEmail) {
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        UserResponse userResponse = authService.getCurrentUser(currentEmail);
        return ResponseEntity.ok(ApiResponse.success(userResponse));
    }

    /**
     * POST /api/auth/logout — Authenticated
     * Blacklist token hiện tại + clear cookie.
     * Hỗ trợ cả cookie lẫn Authorization Bearer header (Swagger / mobile clients).
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Blacklist current token and clear HttpOnly cookie access_token.", security = {
            @SecurityRequirement(name = "cookieAuth"),
            @SecurityRequirement(name = "bearerAuth")
    })
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        // Ưu tiên cookie, fallback sang Authorization header (Swagger / mobile)
        String token = extractToken(request);
        if (token != null) {
            try {
                // Lấy expiry trực tiếp từ claims — không cần JwtProperties ở đây
                java.util.Date expiry = jwtService.getExpiry(token);
                tokenBlacklistService.blacklist(token, expiry);
            } catch (Exception e) {
                // Token malformed / đã expired — vẫn clear cookie
            }
        }

        clearAuthCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công"));
    }

    /**
     * PATCH /api/auth/change-password — Authenticated
     */
    @PatchMapping("/change-password")
    @Operation(summary = "Change password", description = "Change password for currently authenticated user.", security = {
            @SecurityRequirement(name = "cookieAuth"),
            @SecurityRequirement(name = "bearerAuth")
    })
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Old password incorrect or unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal(expression = "username") String currentEmail) {
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        authService.changePassword(currentEmail, request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công"));
    }

    // ============ Cookie helpers ============

    /**
     * Set HttpOnly cookie với JWT token dùng ResponseCookie (Spring).
     * HttpOnly=true → JS không thể đọc (chống XSS).
     * SameSite=Strict → chỉ gửi khi cùng origin (chống CSRF).
     */
    private void setAuthCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(isProductionSecure())
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(Duration.ofDays(1))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Clear cookie khi logout (MaxAge=0).
     */
    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isProductionSecure())
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Đọc token — ưu tiên cookie, fallback sang Authorization header.
     * Giống logic của JwtAuthFilter để đảm bảo cùng token được blacklist.
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Cookie
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> COOKIE_NAME.equals(c.getName()))
                    .map(jakarta.servlet.http.Cookie::getValue)
                    .findFirst()
                    .orElseGet(() -> extractFromHeader(request));
        }
        // 2. Authorization header fallback (Swagger / mobile)
        return extractFromHeader(request);
    }

    private String extractFromHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    /**
     * Secure flag chỉ bật ở production (HTTPS).
     * Có thể inject từ @Value nếu cần tùy chỉnh từng môi trường.
     */
    private boolean isProductionSecure() {
        String profile = System.getProperty("spring.profiles.active", "");
        return profile.contains("prod");
    }
}
