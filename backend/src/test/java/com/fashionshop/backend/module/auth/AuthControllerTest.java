package com.fashionshop.backend.module.auth;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.config.JwtAuthFilter;
import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.config.RateLimitFilter;
import com.fashionshop.backend.module.auth.dto.request.ChangePasswordRequest;
import com.fashionshop.backend.module.auth.dto.request.ForgotPasswordRequest;
import com.fashionshop.backend.module.auth.dto.request.LoginRequest;
import com.fashionshop.backend.module.auth.dto.request.RegisterRequest;
import com.fashionshop.backend.module.auth.dto.request.ResetPasswordRequest;
import com.fashionshop.backend.module.auth.dto.response.AuthResponse;
import com.fashionshop.backend.module.auth.dto.response.UserResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;
    @MockitoBean
    private AuthCookieService authCookieService;

    // Infrastructure beans để WebMvcTest context boot thành công
    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;
    @MockitoBean
    private RateLimitFilter rateLimitFilter;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUpCookieService() {
        when(authCookieService.getCookieName()).thenReturn("access_token");
        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.http.HttpServletResponse response = invocation.getArgument(0);
            String token = invocation.getArgument(1);
            response.addHeader("Set-Cookie", "access_token=" + token + "; Path=/; HttpOnly; SameSite=Lax");
            return null;
        }).when(authCookieService).setAuthCookie(any(), any());
        org.mockito.Mockito.doAnswer(invocation -> {
            jakarta.servlet.http.HttpServletResponse response = invocation.getArgument(0);
            response.addHeader("Set-Cookie", "access_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
            return null;
        }).when(authCookieService).clearAuthCookie(any());
    }

    // ============================
    // POST /api/auth/register
    // ============================

    @Test
    void register_setsCookie_andReturnsUserInfo() throws Exception {
        RegisterRequest request = registerRequest("test@example.com", "Test User", "password123", "0912345678");

        AuthResponse authResponse = AuthResponse.builder()
                .userId(1L).fullName("Test User").email("test@example.com").role(Role.CUSTOMER).build();
        LoginResult loginResult = new LoginResult(authResponse, "token123");

        when(authService.register(any(RegisterRequest.class))).thenReturn(loginResult);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(header().string("Set-Cookie", containsString("access_token=token123")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
    }

    @Test
    void register_whenValidationFails_returnsBadRequest_andDoesNotCallService() throws Exception {
        RegisterRequest request = registerRequest("not-valid-email", " ", "123", null);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    // ============================
    // POST /api/auth/login
    // ============================

    @Test
    void login_setsCookie_andReturnsUserInfo() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password123");

        AuthResponse authResponse = AuthResponse.builder()
                .userId(2L).fullName("Login User").email("user@example.com").role(Role.CUSTOMER).build();
        LoginResult loginResult = new LoginResult(authResponse, "login-token");

        when(authService.login(any(LoginRequest.class))).thenReturn(loginResult);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(header().string("Set-Cookie", containsString("access_token=login-token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    void login_whenValidationFails_returnsBadRequest_andDoesNotCallService() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    // ============================
    // POST /api/auth/forgot-password
    // ============================

    @Test
    void forgotPassword_alwaysReturnsSuccess() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("anyone@example.com");
        doNothing().when(authService).forgotPassword(any());

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("Nếu email tồn tại")));
    }

    // ============================
    // POST /api/auth/reset-password
    // ============================

    @Test
    void resetPassword_returnsSuccess() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("newpassword123");
        doNothing().when(authService).resetPassword(any());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ============================
    // GET /api/auth/me
    // ============================

    @Test
    @WithMockUser(username = "me@example.com", roles = { "CUSTOMER" })
    void me_returnsCurrentUserInfo() throws Exception {
        UserResponse userResponse = UserResponse.builder()
                .userId(5L).fullName("Me").email("me@example.com")
                .role(Role.CUSTOMER).status(UserStatus.ACTIVE).build();

        when(authService.getCurrentUser("me@example.com")).thenReturn(userResponse);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("me@example.com"))
                .andExpect(jsonPath("$.data.userId").value(5));
    }

    // ============================
    // PATCH /api/auth/change-password
    // ============================

    @Test
    @WithMockUser(username = "change@example.com", roles = { "CUSTOMER" })
    void changePassword_usesAuthenticatedEmail_andReturnsSuccess() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldpassword");
        request.setNewPassword("newpassword123");
        doNothing().when(authService).changePassword(eq("change@example.com"), any());

        mockMvc.perform(patch("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).changePassword(eq("change@example.com"), any(ChangePasswordRequest.class));
    }

    // ============================
    // POST /api/auth/logout
    // ============================

    @Test
    void logout_withCookieToken_blacklistsToken_andClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie("access_token", "logout-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(tokenBlacklistService).blacklistToken("logout-token", "LOGOUT");
    }

    @Test
    void logout_withBearerToken_blacklistsToken_andClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer bearer-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(tokenBlacklistService).blacklistToken("bearer-token", "LOGOUT");
    }

    @Test
    void logout_withoutToken_doesNotBlacklist_stillClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(tokenBlacklistService, never()).blacklistToken(any(), any());
    }

    @Test
    void logout_withMalformedToken_doesNotThrow_stillClearsCookie() throws Exception {
        org.mockito.Mockito.doThrow(new io.jsonwebtoken.MalformedJwtException("bad"))
                .when(tokenBlacklistService).blacklistToken("bad.token", "LOGOUT");

        mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie("access_token", "bad.token")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    // ============================
    // Helpers
    // ============================

    private RegisterRequest registerRequest(String email, String fullName, String password, String phone) {
        RegisterRequest r = new RegisterRequest();
        r.setEmail(email);
        r.setFullName(fullName);
        r.setPassword(password);
        r.setPhone(phone);
        return r;
    }
}
