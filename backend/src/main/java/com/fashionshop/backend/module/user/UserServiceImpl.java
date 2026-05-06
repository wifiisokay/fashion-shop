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

    // ================================================================
    // ADMIN METHODS
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public com.fashionshop.backend.common.PageResponse<com.fashionshop.backend.module.user.dto.response.AdminUserResponse> getAdminUsers(String keyword, com.fashionshop.backend.common.enums.Role role, int page, int size) {
        org.springframework.data.jpa.domain.Specification<User> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)
                ));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        org.springframework.data.domain.Page<User> users = userRepository.findAll(
            spec, 
            org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
        );

        java.util.List<com.fashionshop.backend.module.user.dto.response.AdminUserResponse> content = users.getContent().stream()
            .map(com.fashionshop.backend.module.user.dto.response.AdminUserResponse::from)
            .toList();

        return com.fashionshop.backend.common.PageResponse.from(content, users);
    }

    @Override
    @Transactional(readOnly = true)
    public com.fashionshop.backend.module.user.dto.response.UserStatsResponse getUserStats() {
        return com.fashionshop.backend.module.user.dto.response.UserStatsResponse.builder()
            .totalUsers(userRepository.count())
            .activeUsers(userRepository.countByStatus(com.fashionshop.backend.common.enums.UserStatus.ACTIVE))
            .lockedUsers(userRepository.countByStatus(com.fashionshop.backend.common.enums.UserStatus.LOCKED))
            .customerCount(userRepository.countByRole(com.fashionshop.backend.common.enums.Role.CUSTOMER))
            .employeeCount(userRepository.countByRole(com.fashionshop.backend.common.enums.Role.EMPLOYEE))
            .adminCount(userRepository.countByRole(com.fashionshop.backend.common.enums.Role.ADMIN))
            .build();
    }

    @Override
    @Transactional
    public void toggleUserStatus(Long userId, com.fashionshop.backend.common.enums.UserStatus newStatus, User currentUser) {
        if (currentUser.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, org.springframework.http.HttpStatus.BAD_REQUEST, "Bạn không thể tự khóa tài khoản của chính mình");
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
            
        user.setStatus(newStatus);
        userRepository.save(user);
    }
}
