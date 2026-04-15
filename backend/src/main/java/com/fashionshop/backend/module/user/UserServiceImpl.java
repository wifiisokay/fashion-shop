package com.fashionshop.backend.module.user;

import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.storage.StorageService;
import com.fashionshop.backend.module.user.dto.request.UpdateProfileRequest;
import com.fashionshop.backend.module.user.dto.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfileByEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND));

        return UserProfileResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfileByEmail(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND));

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());

        return UserProfileResponse.fromEntity(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserProfileResponse uploadAvatarByEmail(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND));

        String publicId = storageService.buildUserAvatarPublicId(user.getId());
        String avatarUrl = storageService.uploadAvatar(file, publicId);

        user.setAvatarUrl(avatarUrl);
        return UserProfileResponse.fromEntity(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserProfileResponse removeAvatarByEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> BusinessException.notFound(ErrorCode.USER_NOT_FOUND));

        String publicId = storageService.buildUserAvatarPublicId(user.getId());
        storageService.deleteAvatar(publicId);

        user.setAvatarUrl(null);
        return UserProfileResponse.fromEntity(userRepository.save(user));
    }
}
