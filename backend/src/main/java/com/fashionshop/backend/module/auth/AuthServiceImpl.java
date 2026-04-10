package com.fashionshop.backend.module.auth;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.auth.dto.request.ChangePasswordRequest;
import com.fashionshop.backend.module.auth.dto.request.ForgotPasswordRequest;
import com.fashionshop.backend.module.auth.dto.request.LoginRequest;
import com.fashionshop.backend.module.auth.dto.request.RegisterRequest;
import com.fashionshop.backend.module.auth.dto.request.ResetPasswordRequest;
import com.fashionshop.backend.module.auth.dto.response.AuthResponse;
import com.fashionshop.backend.module.auth.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final PasswordResetMailService passwordResetMailService;

    @Override
    public LoginResult register(RegisterRequest request) {
        // Kiểm tra email đã tồn tại
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(Role.CUSTOMER)
                .build();

        userRepository.save(user);

        // Generate token ngay bên trong service — không cần Controller truy cập DB
        String token = jwtService.generateToken(user);
        return new LoginResult(toAuthResponse(user), token);
    }

    @Override
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED));

        // Kiểm tra tài khoản bị khóa
        if (!user.isAccountNonLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, HttpStatus.FORBIDDEN);
        }

        // Kiểm tra mật khẩu
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }

        String token = jwtService.generateToken(user);
        return new LoginResult(toAuthResponse(user), token);
    }

    @Override
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        return UserResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .build();
    }

    @Override
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Mật khẩu hiện tại không đúng");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

        // Luôn trả success ở controller để tránh user enumeration.
        if (userOpt.isEmpty()) {
            return;
        }

        String resetToken = passwordResetTokenService.createToken(request.getEmail());
        boolean sent = passwordResetMailService.sendResetPasswordEmail(request.getEmail(), resetToken);
        if (!sent) {
            log.warn("Reset password email was not sent for {}. Verify SMTP credentials/config.", request.getEmail());
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String email = passwordResetTokenService.consumeToken(request.getToken());
        if (email == null) {
            throw new BusinessException(
                    ErrorCode.RESET_TOKEN_INVALID,
                    HttpStatus.BAD_REQUEST,
                    "Reset token không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(
                    ErrorCode.PASSWORD_REUSE_NOT_ALLOWED,
                    HttpStatus.BAD_REQUEST,
                    "Mật khẩu mới không được trùng với mật khẩu hiện tại");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // ============ Private helpers ============

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
