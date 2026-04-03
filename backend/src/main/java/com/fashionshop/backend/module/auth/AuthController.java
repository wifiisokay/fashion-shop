package com.fashionshop.backend.module.auth;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.config.JwtProperties;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.auth.dto.*;
import com.fashionshop.backend.module.auth.entity.User;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(
    name = "Authentication",
    description = "Cookie JWT authentication APIs. Access token is stored in HttpOnly cookie 'access_token'."
)
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * POST /api/auth/register — Public
     * Tạo tài khoản, set HttpOnly cookie, trả về thông tin user.
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register new account",
        description = "Create customer account and set HttpOnly cookie access_token. Token is not returned in response body."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Register successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"success\":false,\"message\":\"email: Email không đúng định dạng\",\"errorCode\":\"VALIDATION_001\",\"timestamp\":\"2026-03-31T15:00:00Z\"}")
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Email already exists",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Email đã được sử dụng\",\"errorCode\":\"AUTH_002\",\"timestamp\":\"2026-03-31T15:00:00Z\"}")
            )
        )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(
        @Valid @RequestBody RegisterRequest request,
        HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.register(request);

        // Load user để generate token
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR));
        String token = authService.generateToken(user);
        setAuthCookie(response, token);

        return ResponseEntity.ok(ApiResponse.success("Đăng ký thành công", authResponse));
    }

    /**
     * POST /api/auth/login — Public
     * Đăng nhập, set HttpOnly cookie, trả về thông tin user.
     */
    @PostMapping("/login")
    @Operation(
        summary = "Login",
        description = "Authenticate by email/password and set HttpOnly cookie access_token."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Email hoặc mật khẩu không đúng\",\"errorCode\":\"AUTH_003\",\"timestamp\":\"2026-03-31T15:00:00Z\"}")
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Account locked",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Tài khoản đã bị khóa\",\"errorCode\":\"AUTH_004\",\"timestamp\":\"2026-03-31T15:00:00Z\"}")
            )
        )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request);

        // Load user để generate token
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR));
        String token = authService.generateToken(user);
        setAuthCookie(response, token);

        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", authResponse));
    }

    /**
     * POST /api/auth/forgot-password — Public
     * Sinh reset token một lần dùng và gửi qua kênh out-of-band (email/log).
     */
    @PostMapping("/forgot-password")
    @Operation(
        summary = "Forgot password",
        description = "Generate one-time reset token. Always returns success message to avoid revealing whether email exists."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request accepted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
            "Nếu email tồn tại, hệ thống đã gửi hướng dẫn đặt lại mật khẩu"
        ));
    }

    /**
     * POST /api/auth/reset-password — Public
     * Đặt lại mật khẩu bằng reset token.
     */
    @PostMapping("/reset-password")
    @Operation(
        summary = "Reset password",
        description = "Reset password using one-time token from forgot-password flow."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid token or validation error")
    })
    public ResponseEntity<ApiResponse<Void>> resetPassword(
        @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công"));
    }

    /**
     * GET /api/auth/me — Authenticated
     * Lấy thông tin user hiện tại từ token trong cookie.
     */
    @GetMapping("/me")
    @Operation(
        summary = "Get current user",
        description = "Read authenticated user info from JWT token in HttpOnly cookie (or Bearer token fallback).",
        security = {
            @SecurityRequirement(name = "cookieAuth"),
            @SecurityRequirement(name = "bearerAuth")
        }
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current user returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated or invalid token"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse<UserResponse>> me(
        @AuthenticationPrincipal User currentUser
    ) {
        UserResponse userResponse = authService.getCurrentUser(currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.success(userResponse));
    }

    /**
     * POST /api/auth/logout — Authenticated
     * Blacklist token hiện tại + clear cookie.
     */
    @PostMapping("/logout")
    @Operation(
        summary = "Logout",
        description = "Blacklist current token and clear HttpOnly cookie access_token.",
        security = {
            @SecurityRequirement(name = "cookieAuth"),
            @SecurityRequirement(name = "bearerAuth")
        }
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    public ResponseEntity<ApiResponse<Void>> logout(
        HttpServletRequest request,
        HttpServletResponse response,
        @AuthenticationPrincipal User currentUser
    ) {
        // Lấy token từ cookie để blacklist
        String token = extractTokenFromCookie(request);
        if (token != null) {
            tokenBlacklistService.blacklist(token, new java.util.Date(
                System.currentTimeMillis() + jwtProperties.getExpiration()
            ));
        }

        // Clear cookie
        clearAuthCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công"));
    }

    /**
     * PATCH /api/auth/change-password — Authenticated
     */
    @PatchMapping("/change-password")
    @Operation(
        summary = "Change password",
        description = "Change password for currently authenticated user.",
        security = {
            @SecurityRequirement(name = "cookieAuth"),
            @SecurityRequirement(name = "bearerAuth")
        }
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Old password incorrect or unauthenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse<Void>> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        authService.changePassword(currentUser.getEmail(), request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công"));
    }

    // ============ Cookie helpers ============

    /**
     * Set HttpOnly cookie với JWT token.
     * HttpOnly=true → JS không thể đọc (chống XSS).
     * SameSite=Strict → chỉ gửi khi cùng origin (chống CSRF).
     */
    private void setAuthCookie(HttpServletResponse response, String token) {
        JwtProperties.Cookie cookieConfig = jwtProperties.getCookie();
        // Set cookie qua header để luôn kiểm soát được SameSite.
        response.addHeader("Set-Cookie",
            cookieConfig.getName() + "=" + token +
            "; HttpOnly" +
            (cookieConfig.isSecure() ? "; Secure" : "") +
            "; SameSite=" + cookieConfig.getSameSite() +
            "; Path=" + cookieConfig.getPath() +
            "; Max-Age=" + cookieConfig.getMaxAge()
        );
    }

    /**
     * Clear cookie khi logout.
     */
    private void clearAuthCookie(HttpServletResponse response) {
        JwtProperties.Cookie cookieConfig = jwtProperties.getCookie();
        response.addHeader("Set-Cookie",
            cookieConfig.getName() + "=" +
            "; HttpOnly" +
            (cookieConfig.isSecure() ? "; Secure" : "") +
            "; SameSite=" + cookieConfig.getSameSite() +
            "; Path=" + cookieConfig.getPath() +
            "; Max-Age=0"
        );
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
            .filter(c -> jwtProperties.getCookie().getName().equals(c.getName()))
            .map(jakarta.servlet.http.Cookie::getValue)
            .findFirst()
            .orElse(null);
    }
}
