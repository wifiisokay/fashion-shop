package com.fashionshop.backend.module.auth;

import com.fashionshop.backend.module.auth.dto.request.ChangePasswordRequest;
import com.fashionshop.backend.module.auth.dto.request.ForgotPasswordRequest;
import com.fashionshop.backend.module.auth.dto.request.LoginRequest;
import com.fashionshop.backend.module.auth.dto.request.RegisterRequest;
import com.fashionshop.backend.module.auth.dto.request.ResetPasswordRequest;
import com.fashionshop.backend.module.auth.dto.response.UserResponse;

/**
 * Auth service interface — theo convention interface/impl của project.
 */
public interface AuthService {

    /**
     * Đăng ký tài khoản mới.
     * Trả về {@link LoginResult} chứa thông tin user + JWT token (để Controller set cookie).
     * Token KHÔNG được trả về trong HTTP body.
     */
    LoginResult register(RegisterRequest request);

    /**
     * Đăng nhập bằng email/password.
     * Trả về {@link LoginResult} chứa thông tin user + JWT token (để Controller set cookie).
     */
    LoginResult login(LoginRequest request);

    /**
     * Lấy thông tin user hiện tại (GET /auth/me).
     */
    UserResponse getCurrentUser(String email);

    /**
     * Đổi mật khẩu.
     */
    void changePassword(String email, ChangePasswordRequest request);

    /**
     * Quên mật khẩu: tạo reset token một lần dùng và gửi qua kênh out-of-band (email).
     * Luôn trả success message ở Controller để tránh lộ email có tồn tại hay không.
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Đặt lại mật khẩu bằng reset token.
     */
    void resetPassword(ResetPasswordRequest request);
}
