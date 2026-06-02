package com.fashionshop.backend.module.auth;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordResetTokenService passwordResetTokenService;
    @Mock
    private PasswordResetMailService passwordResetMailService;

    @InjectMocks
    private AuthServiceImpl authService;

    // ============================
    // register()
    // ============================

    @Test
    void register_whenEmailExists_throwsConflict() {
        RegisterRequest request = registerRequest("exists@example.com", "Test User", "password123", null);
        when(userRepository.existsByEmail("exists@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);

        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void register_whenValid_savesEncodedPassword() {
        RegisterRequest request = registerRequest("new@example.com", "New User", "password123", "0912345678");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-pass");
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getPassword()).isEqualTo("encoded-pass");
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getPhone()).isEqualTo("0912345678");
    }

    @Test
    void register_whenValid_returnsLoginResultWithToken() {
        RegisterRequest request = registerRequest("new@example.com", "New User", "password123", null);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        LoginResult result = authService.register(request);

        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.userInfo().getEmail()).isEqualTo("new@example.com");
        assertThat(result.userInfo().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void register_whenValid_callsJwtGenerateToken() {
        RegisterRequest request = registerRequest("new@example.com", "User", "password123", null);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(jwtService.generateToken(any(User.class))).thenReturn("token");

        authService.register(request);

        verify(jwtService).generateToken(any(User.class));
    }

    // ============================
    // login()
    // ============================

    @Test
    void login_whenEmailMissing_throwsUnauthorized() {
        LoginRequest request = loginRequest("missing@example.com", "password123");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_whenAccountLocked_throwsForbidden() {
        LoginRequest request = loginRequest("locked@example.com", "password123");
        when(userRepository.findByEmail("locked@example.com"))
                .thenReturn(Optional.of(baseUser("locked@example.com", "encoded", UserStatus.LOCKED)));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_LOCKED)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }

    @Test
    void login_whenPasswordMismatch_throwsUnauthorized() {
        LoginRequest request = loginRequest("user@example.com", "wrongpass");
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(baseUser("user@example.com", "encoded", UserStatus.ACTIVE)));
        when(passwordEncoder.matches("wrongpass", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_whenValid_returnsLoginResultWithTokenAndUserInfo() {
        LoginRequest request = loginRequest("user@example.com", "correctpass");
        User user = baseUser("user@example.com", "encoded", UserStatus.ACTIVE);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctpass", "encoded")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-login-token");

        LoginResult result = authService.login(request);

        assertThat(result.token()).isEqualTo("jwt-login-token");
        assertThat(result.userInfo().getEmail()).isEqualTo("user@example.com");
        assertThat(result.userInfo().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void login_whenValid_callsJwtGenerateToken() {
        LoginRequest request = loginRequest("user@example.com", "correctpass");
        User user = baseUser("user@example.com", "encoded", UserStatus.ACTIVE);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctpass", "encoded")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("token");

        authService.login(request);

        verify(jwtService).generateToken(user);
    }

    // ============================
    // getCurrentUser()
    // ============================

    @Test
    void getCurrentUser_whenUserMissing_throwsNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser("missing@example.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void getCurrentUser_whenUserExists_returnsMappedResponse() {
        User user = baseUser("me@example.com", "encoded", UserStatus.ACTIVE);
        user.setPhone("0912345678");
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));

        var response = authService.getCurrentUser("me@example.com");

        assertThat(response.getEmail()).isEqualTo("me@example.com");
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.getPhone()).isEqualTo("0912345678");
    }

    // ============================
    // changePassword()
    // ============================

    @Test
    void changePassword_whenUserMissing_throwsNotFound() {
        ChangePasswordRequest request = changePasswordRequest("oldpass", "newpass123");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword("missing@example.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void changePassword_whenOldPasswordIncorrect_throwsUnauthorized() {
        ChangePasswordRequest request = changePasswordRequest("oldpass", "newpass123");
        User user = baseUser("change@example.com", "encoded", UserStatus.ACTIVE);
        when(userRepository.findByEmail("change@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("change@example.com", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS)
                .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changePassword_whenValid_updatesPasswordAndSaves() {
        ChangePasswordRequest request = changePasswordRequest("oldpass", "newpass123");
        User user = baseUser("change@example.com", "encoded", UserStatus.ACTIVE);
        when(userRepository.findByEmail("change@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("new-encoded");

        authService.changePassword("change@example.com", request);

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    // ============================
    // forgotPassword()
    // ============================

    @Test
    void forgotPassword_whenUserMissing_doesNothingAndNoException() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        // Should not throw, should not call downstream services
        authService.forgotPassword(request);

        verify(passwordResetTokenService, never()).createToken(any());
        verify(passwordResetMailService, never()).sendResetPasswordEmail(any(), any());
    }

    @Test
    void forgotPassword_whenUserExists_createsTokenAndSendsEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("exists@example.com");
        User user = baseUser("exists@example.com", "encoded", UserStatus.ACTIVE);
        when(userRepository.findByEmail("exists@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenService.createToken("exists@example.com")).thenReturn("reset-token");

        authService.forgotPassword(request);

        verify(passwordResetTokenService).createToken("exists@example.com");
        verify(passwordResetMailService).sendResetPasswordEmail("exists@example.com", "reset-token");
    }

    @Test
    void forgotPassword_whenMailFails_doesNotThrow() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("exists@example.com");
        User user = baseUser("exists@example.com", "encoded", UserStatus.ACTIVE);
        when(userRepository.findByEmail("exists@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenService.createToken("exists@example.com")).thenReturn("reset-token");
        when(passwordResetMailService.sendResetPasswordEmail("exists@example.com", "reset-token")).thenReturn(false);

        // Mail failure should not propagate as exception
        authService.forgotPassword(request);

        verify(passwordResetMailService).sendResetPasswordEmail("exists@example.com", "reset-token");
    }

    // ============================
    // resetPassword()
    // ============================

    @Test
    void resetPassword_whenTokenInvalid_throwsBadRequest() {
        ResetPasswordRequest request = resetPasswordRequest("bad-token", "newpass123");
        when(passwordResetTokenService.consumeToken("bad-token")).thenReturn(null);

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESET_TOKEN_INVALID)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void resetPassword_whenUserMissing_throwsNotFound() {
        ResetPasswordRequest request = resetPasswordRequest("good-token", "newpass123");
        when(passwordResetTokenService.consumeToken("good-token")).thenReturn("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void resetPassword_whenReusePassword_throwsBadRequest() {
        ResetPasswordRequest request = resetPasswordRequest("good-token", "samepass");
        User user = baseUser("reuse@example.com", "encoded", UserStatus.ACTIVE);
        when(passwordResetTokenService.consumeToken("good-token")).thenReturn("reuse@example.com");
        when(userRepository.findByEmail("reuse@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("samepass", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PASSWORD_REUSE_NOT_ALLOWED)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_whenValid_updatesPasswordAndSaves() {
        ResetPasswordRequest request = resetPasswordRequest("good-token", "newpass123");
        User user = baseUser("reset@example.com", "encoded", UserStatus.ACTIVE);
        when(passwordResetTokenService.consumeToken("good-token")).thenReturn("reset@example.com");
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("newpass123", "encoded")).thenReturn(false);
        when(passwordEncoder.encode("newpass123")).thenReturn("new-encoded");

        authService.resetPassword(request);

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_whenValid_consumesTokenExactlyOnce() {
        ResetPasswordRequest request = resetPasswordRequest("one-time-token", "newpass123");
        User user = baseUser("reset@example.com", "encoded", UserStatus.ACTIVE);
        when(passwordResetTokenService.consumeToken("one-time-token")).thenReturn("reset@example.com");
        when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("newpass123", "encoded")).thenReturn(false);
        when(passwordEncoder.encode("newpass123")).thenReturn("new-encoded");

        authService.resetPassword(request);

        verify(passwordResetTokenService).consumeToken("one-time-token");
    }

    // ============================
    // Helpers
    // ============================

    private User baseUser(String email, String password, UserStatus status) {
        return User.builder()
                .id(10L)
                .email(email)
                .fullName("Test User")
                .password(password)
                .status(status)
                .role(Role.CUSTOMER)
                .build();
    }

    private RegisterRequest registerRequest(String email, String fullName, String password, String phone) {
        RegisterRequest r = new RegisterRequest();
        r.setEmail(email);
        r.setFullName(fullName);
        r.setPassword(password);
        r.setPhone(phone);
        return r;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    private ChangePasswordRequest changePasswordRequest(String oldPassword, String newPassword) {
        ChangePasswordRequest r = new ChangePasswordRequest();
        r.setOldPassword(oldPassword);
        r.setNewPassword(newPassword);
        return r;
    }

    private ResetPasswordRequest resetPasswordRequest(String token, String newPassword) {
        ResetPasswordRequest r = new ResetPasswordRequest();
        r.setToken(token);
        r.setNewPassword(newPassword);
        return r;
    }
}
