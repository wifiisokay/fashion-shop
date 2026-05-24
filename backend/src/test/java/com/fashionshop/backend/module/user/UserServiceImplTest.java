package com.fashionshop.backend.module.user;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.storage.StorageService;
import com.fashionshop.backend.module.user.dto.request.UpdateProfileRequest;
import com.fashionshop.backend.module.user.dto.response.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private UserServiceImpl userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@shop.com");
        mockUser.setPassword("encoded");
        mockUser.setFullName("Old Name");
        mockUser.setPhone("0912345678");
        mockUser.setAvatarUrl("https://old-avatar");
        mockUser.setRole(Role.CUSTOMER);
        mockUser.setStatus(UserStatus.ACTIVE);
    }

    // ============ getProfileByEmail ============

    @Test
    void getProfileByEmail_returnsMappedProfile_whenUserExists() {
        when(userRepository.findByEmail("test@shop.com")).thenReturn(Optional.of(mockUser));

        UserProfileResponse response = userService.getProfileByEmail("test@shop.com");

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("test@shop.com");
        assertThat(response.getFullName()).isEqualTo("Old Name");
    }

    @Test
    void getProfileByEmail_throwsNotFound_whenUserMissing() {
        when(userRepository.findByEmail("missing@shop.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfileByEmail("missing@shop.com"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // ============ updateProfileByEmail ============

    @Test
    void updateProfileByEmail_updatesAllowedFieldsOnly() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("New Name");
        request.setPhone("0987654321");

        when(userRepository.findByEmail("test@shop.com")).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateProfileByEmail("test@shop.com", request);

        assertThat(response.getFullName()).isEqualTo("New Name");
        assertThat(response.getPhone()).isEqualTo("0987654321");
        assertThat(response.getEmail()).isEqualTo("test@shop.com");
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
        // avatarUrl không thay đổi qua PUT /profile — vẫn là giá trị cũ
        assertThat(response.getAvatarUrl()).isEqualTo("https://old-avatar");
    }

    @Test
    void updateProfileByEmail_throwsNotFound_whenUserMissing() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("New Name");

        when(userRepository.findByEmail("missing@shop.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfileByEmail("missing@shop.com", request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // ============ uploadAvatarByEmail ============

    @Test
    void uploadAvatarByEmail_uploadsToStorage_andUpdatesAvatarUrl() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(userRepository.findByEmail("test@shop.com")).thenReturn(Optional.of(mockUser));
        when(storageService.buildUserAvatarPublicId(1L)).thenReturn("fashion-shop/users/1/avatar");
        when(storageService.uploadAvatar(file, "fashion-shop/users/1/avatar"))
            .thenReturn("https://cloudinary/new-avatar");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.uploadAvatarByEmail("test@shop.com", file);

        assertThat(response.getAvatarUrl()).isEqualTo("https://cloudinary/new-avatar");
        verify(storageService).uploadAvatar(file, "fashion-shop/users/1/avatar");
    }

    @Test
    void uploadAvatarByEmail_throwsNotFound_whenUserMissing() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(userRepository.findByEmail("missing@shop.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.uploadAvatarByEmail("missing@shop.com", file))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(storageService, never()).uploadAvatar(any(), any());
    }

    @Test
    void uploadAvatarByEmail_propagatesException_whenStorageThrows() {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(userRepository.findByEmail("test@shop.com")).thenReturn(Optional.of(mockUser));
        when(storageService.buildUserAvatarPublicId(1L)).thenReturn("fashion-shop/users/1/avatar");
        when(storageService.uploadAvatar(any(), any()))
            .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Upload thất bại"));

        assertThatThrownBy(() -> userService.uploadAvatarByEmail("test@shop.com", file))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_ERROR);
    }

    // ============ removeAvatarByEmail ============

    @Test
    void removeAvatarByEmail_deletesStorageResource_andClearsAvatarUrl() {
        when(userRepository.findByEmail("test@shop.com")).thenReturn(Optional.of(mockUser));
        when(storageService.buildUserAvatarPublicId(1L)).thenReturn("fashion-shop/users/1/avatar");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.removeAvatarByEmail("test@shop.com");

        verify(storageService).deleteAvatar("fashion-shop/users/1/avatar");
        assertThat(response.getAvatarUrl()).isNull();
    }

    @Test
    void removeAvatarByEmail_throwsNotFound_whenUserMissing() {
        when(userRepository.findByEmail("missing@shop.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.removeAvatarByEmail("missing@shop.com"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

        verify(storageService, never()).deleteAvatar(any());
    }

    @Test
    void removeAvatarByEmail_stillCallsDeleteOnStorage_whenAvatarUrlAlreadyNull() {
        // Cloudinary destroy trả "not found" nhưng không throw — đây là expected behavior
        mockUser.setAvatarUrl(null);
        when(userRepository.findByEmail("test@shop.com")).thenReturn(Optional.of(mockUser));
        when(storageService.buildUserAvatarPublicId(1L)).thenReturn("fashion-shop/users/1/avatar");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.removeAvatarByEmail("test@shop.com");

        // Vẫn gọi delete để đảm bảo Cloudinary side sạch
        verify(storageService).deleteAvatar("fashion-shop/users/1/avatar");
        assertThat(response.getAvatarUrl()).isNull();
    }
}
