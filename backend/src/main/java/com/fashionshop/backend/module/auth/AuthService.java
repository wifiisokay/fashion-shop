package com.fashionshop.backend.module.auth;

import com.fashionshop.backend.module.auth.dto.*;

/**
 * Auth service interface — theo convention interface/impl của project.
 */
public interface AuthService {

    /**
     * Đăng ký tài khoản mới. Trả về AuthResponse (không chứa token).
     * Token được generate bởi Controller và set vào HttpOnly cookie.
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Đăng nhập. Trả về AuthResponse.
     * Token được generate bởi Controller và set vào HttpOnly cookie.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Lấy thông tin user hiện tại (GET /auth/me).
     */
    UserResponse getCurrentUser(String email);

    /**
     * Đổi mật khẩu.
     */
    void changePassword(String email, ChangePasswordRequest request);

    /**
     * Generate JWT token cho user — dùng bởi Controller.
     * Tách ra để Controller có thể set cookie sau khi nhận token.
     */
    String generateToken(com.fashionshop.backend.module.auth.entity.User user);

    /**
     * Quên mật khẩu: tạo reset token một lần dùng và gửi qua kênh out-of-band (email/log).
     * Luôn trả success message để tránh lộ email có tồn tại hay không.
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Đặt lại mật khẩu bằng reset token.
     */
    void resetPassword(ResetPasswordRequest request);
}
