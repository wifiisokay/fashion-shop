package com.fashionshop.backend.module.user;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.config.JwtAuthFilter;
import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.config.RateLimitFilter;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.user.dto.request.UpdateProfileRequest;
import com.fashionshop.backend.module.user.dto.response.UserProfileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtAuthFilter jwtAuthFilter;
    @MockitoBean
    private RateLimitFilter rateLimitFilter;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private User mockUser;
    private UserProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@shop.com");
        mockUser.setPassword("encoded");
        mockUser.setFullName("Test User");
        mockUser.setRole(Role.CUSTOMER);
        mockUser.setStatus(UserStatus.ACTIVE);

        profileResponse = UserProfileResponse.builder()
            .userId(1L)
            .fullName("Test User")
            .email("test@shop.com")
            .phone("0912345678")
            .role(Role.CUSTOMER)
            .avatarUrl("https://res.cloudinary.com/demo/image/upload/avatar.jpg")
            .status(UserStatus.ACTIVE)
            .build();

        UsernamePasswordAuthenticationToken auth =
            UsernamePasswordAuthenticationToken.authenticated(
                mockUser,
                null,
                mockUser.getAuthorities()
            );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ============ GET /profile ============

    @Test
    void getProfile_returns200_whenAuthenticated() throws Exception {
        when(userService.getProfileByEmail("test@shop.com")).thenReturn(profileResponse);

        mockMvc.perform(get("/api/user/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("test@shop.com"))
            .andExpect(jsonPath("$.data.userId").value(1L));
    }

    @Test
    void getProfile_returns401_whenPrincipalNull() throws Exception {
        // Clear auth → principal null → validateCurrentEmail throws 401
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/user/profile"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ============ PUT /profile ============

    @Test
    void updateProfile_returns200_whenValidRequest() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");
        request.setPhone("0912345678");

        UserProfileResponse updated = UserProfileResponse.builder()
            .userId(1L)
            .fullName("Updated Name")
            .email("test@shop.com")
            .phone("0912345678")
            .role(Role.CUSTOMER)
            .avatarUrl("https://res.cloudinary.com/demo/image/upload/avatar.jpg")
            .status(UserStatus.ACTIVE)
            .build();

        when(userService.updateProfileByEmail(eq("test@shop.com"), any(UpdateProfileRequest.class)))
            .thenReturn(updated);

        mockMvc.perform(put("/api/user/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fullName").value("Updated Name"));
    }

    @Test
    void updateProfile_returns400_whenPhoneInvalid() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");
        request.setPhone("01234"); // invalid

        mockMvc.perform(put("/api/user/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateProfile_returns400_whenFullNameBlank() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("   "); // blank
        request.setPhone("0912345678");

        mockMvc.perform(put("/api/user/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ============ POST /profile/avatar ============

    @Test
    void uploadAvatar_returns200_whenValidImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.jpg",
            "image/jpeg",
            "fake-image-content".getBytes()
        );

        when(userService.uploadAvatarByEmail(eq("test@shop.com"), any())).thenReturn(profileResponse);

        mockMvc.perform(multipart("/api/user/profile/avatar")
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.avatarUrl").value("https://res.cloudinary.com/demo/image/upload/avatar.jpg"));
    }

    @Test
    void uploadAvatar_returns400_whenServiceThrowsInvalidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.txt",
            "text/plain",
            "not-image".getBytes()
        );

        when(userService.uploadAvatarByEmail(eq("test@shop.com"), any()))
            .thenThrow(new BusinessException(ErrorCode.INVALID_FILE_TYPE, HttpStatus.BAD_REQUEST));

        mockMvc.perform(multipart("/api/user/profile/avatar")
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void uploadAvatar_returns400_whenServiceThrowsFileTooLarge() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "big.jpg",
            "image/jpeg",
            new byte[6 * 1024 * 1024] // 6MB
        );

        when(userService.uploadAvatarByEmail(eq("test@shop.com"), any()))
            .thenThrow(new BusinessException(ErrorCode.FILE_TOO_LARGE, HttpStatus.BAD_REQUEST));

        mockMvc.perform(multipart("/api/user/profile/avatar")
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ============ DELETE /profile/avatar ============

    @Test
    void removeAvatar_returns200_whenSuccess() throws Exception {
        UserProfileResponse removedAvatarResponse = UserProfileResponse.builder()
            .userId(1L)
            .fullName("Test User")
            .email("test@shop.com")
            .phone("0912345678")
            .role(Role.CUSTOMER)
            .avatarUrl(null)
            .status(UserStatus.ACTIVE)
            .build();

        when(userService.removeAvatarByEmail("test@shop.com")).thenReturn(removedAvatarResponse);

        mockMvc.perform(delete("/api/user/profile/avatar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.avatarUrl").doesNotExist());
    }
}
