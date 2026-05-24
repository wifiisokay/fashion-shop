package com.fashionshop.backend.module.user;

import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.storage.StorageService;
import com.fashionshop.backend.module.user.dto.request.UpdateProfileRequest;
import com.fashionshop.backend.module.user.dto.request.CreateStaffRequest;
import com.fashionshop.backend.module.user.dto.response.UserProfileResponse;
import com.fashionshop.backend.module.user.dto.response.AdminUserResponse;
import com.fashionshop.backend.module.user.dto.response.CreateStaffResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final PasswordEncoder passwordEncoder;

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
    public com.fashionshop.backend.common.PageResponse<com.fashionshop.backend.module.user.dto.response.AdminUserResponse> getAdminUsers(
            String keyword,
            com.fashionshop.backend.common.enums.Role role,
            com.fashionshop.backend.common.enums.UserStatus status,
            int page,
            int size) {
        org.springframework.data.jpa.domain.Specification<User> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern)
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
        updateUserStatus(userId, newStatus, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getAdminUserDetail(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        return AdminUserResponse.from(user);
    }

    @Override
    @Transactional
    public CreateStaffResponse createStaff(CreateStaffRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT, "Email đã được sử dụng");
        }

        String rawPassword = request.getPassword();
        String tempPassword = null;
        if (rawPassword == null || rawPassword.isBlank()) {
            tempPassword = generateTempPassword();
            rawPassword = tempPassword;
        }

        User user = User.builder()
            .email(email)
            .password(passwordEncoder.encode(rawPassword))
            .fullName(request.getFullName().trim())
            .phone(request.getPhone())
            .role(com.fashionshop.backend.common.enums.Role.EMPLOYEE)
            .status(com.fashionshop.backend.common.enums.UserStatus.ACTIVE)
            .build();

        User saved = userRepository.save(user);
        return CreateStaffResponse.builder()
            .user(AdminUserResponse.from(saved))
            .tempPassword(tempPassword)
            .build();
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserRole(Long targetUserId, com.fashionshop.backend.common.enums.Role role, User currentUser) {
        if (currentUser.getId().equals(targetUserId) && role != com.fashionshop.backend.common.enums.Role.ADMIN) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Bạn không thể thay đổi quyền của chính mình");
        }

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));

        if (user.getRole() == com.fashionshop.backend.common.enums.Role.ADMIN
            && role != com.fashionshop.backend.common.enums.Role.ADMIN
            && isLastActiveAdmin(user.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Không thể thay đổi admin cuối cùng");
        }

        user.setRole(role);
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserStatus(Long targetUserId, com.fashionshop.backend.common.enums.UserStatus status, User currentUser) {
        if (currentUser.getId().equals(targetUserId) && status == com.fashionshop.backend.common.enums.UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Bạn không thể tự khóa tài khoản của chính mình");
        }

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));

        if (user.getRole() == com.fashionshop.backend.common.enums.Role.ADMIN
            && status == com.fashionshop.backend.common.enums.UserStatus.LOCKED
            && isLastActiveAdmin(user.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Không thể khóa admin cuối cùng");
        }

        user.setStatus(status);
        return AdminUserResponse.from(userRepository.save(user));
    }

    private boolean isLastActiveAdmin(Long userId) {
        long activeAdmins = userRepository.countByRoleAndStatus(
            com.fashionshop.backend.common.enums.Role.ADMIN,
            com.fashionshop.backend.common.enums.UserStatus.ACTIVE
        );
        return activeAdmins <= 1;
    }

    private String generateTempPassword() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
