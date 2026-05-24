package com.fashionshop.backend.module.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryStorageService implements StorageService {

    // ============ Constants ============

    private static final long MAX_AVATAR_SIZE_BYTES = 5 * 1024 * 1024L; // 5MB
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private static final String FOLDER_PREFIX     = "fashion-shop/users/";
    private static final String AVATAR_SUFFIX     = "/avatar";
    private static final String RESOURCE_TYPE_IMG = "image";

    private final Cloudinary cloudinary;

    // ============ StorageService contract ============

    @Override
    public String buildUserAvatarPublicId(Long userId) {
        return FOLDER_PREFIX + userId + AVATAR_SUFFIX;
    }

    /**
     * Upload ảnh avatar lên Cloudinary.
     * Validate → đọc bytes → upload → trả secure_url.
     *
     * @throws BusinessException 400 nếu file không hợp lệ / quá lớn
     * @throws BusinessException 500 nếu Cloudinary lỗi hoặc không trả URL
     */
    @Override
    public String uploadAvatar(MultipartFile file, String publicId) {
        validateImageFile(file);
        byte[] bytes = readBytes(file, publicId);
        return doUpload(bytes, publicId);
    }

    /**
     * Xóa avatar khỏi Cloudinary.
     * Không throw nếu file không tồn tại ("not found" là kết quả chấp nhận được).
     *
     * @throws BusinessException 500 nếu Cloudinary gặp lỗi thực sự
     */
    @Override
    public void deleteAvatar(String publicId) {
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap("resource_type", RESOURCE_TYPE_IMG, "invalidate", true)
            );

            String resultStatus = String.valueOf(result.get("result"));
            if (!"ok".equals(resultStatus) && !"not found".equals(resultStatus)) {
                log.warn("Cloudinary destroy trả kết quả lạ publicId={} result={}", publicId, resultStatus);
            }

        } catch (IOException ex) {
            log.error("IOException khi xóa avatar publicId={}", publicId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Xóa avatar thất bại");
        } catch (RuntimeException ex) {
            log.error("RuntimeException khi xóa avatar publicId={}: {}", publicId, ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Xóa avatar thất bại");
        }
    }

    // ============ Private helpers ============

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_FILE_TYPE);
        }

        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw BusinessException.badRequest(ErrorCode.FILE_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            log.debug("File bị từ chối vì contentType=[{}] không nằm trong whitelist", contentType);
            throw BusinessException.badRequest(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private byte[] readBytes(MultipartFile file, String publicId) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            log.error("Không thể đọc bytes từ file khi upload avatar publicId={}", publicId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu ảnh");
        }
    }

    private String doUpload(byte[] imageBytes, String publicId) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                imageBytes,
                ObjectUtils.asMap(
                    "public_id",     publicId,
                    "resource_type", RESOURCE_TYPE_IMG,
                    "overwrite",     true,
                    "invalidate",    true
                )
            );

            Object secureUrl = result.get("secure_url");
            if (secureUrl == null) {
                log.error("Cloudinary upload thành công nhưng không trả secure_url publicId={}", publicId);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Upload avatar thất bại");
            }

            return secureUrl.toString();

        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("IOException khi upload avatar publicId={}", publicId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Upload avatar thất bại");
        } catch (RuntimeException ex) {
            log.error("RuntimeException khi upload avatar publicId={}: {}", publicId, ex.getMessage(), ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Upload avatar thất bại");
        }
    }

    // ============ Generic image upload (Product, v.v.) ============

    @Override
    public UploadResult uploadImage(MultipartFile file, String folder) {
        validateImageFile(file);
        String publicId = folder + "/" + java.util.UUID.randomUUID();
        byte[] bytes = readBytes(file, publicId);
        String url = doUpload(bytes, publicId);
        return new UploadResult(url, publicId);
    }

    @Override
    public void deleteImage(String publicId) {
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap("resource_type", RESOURCE_TYPE_IMG, "invalidate", true)
            );

            String resultStatus = String.valueOf(result.get("result"));
            if (!"ok".equals(resultStatus) && !"not found".equals(resultStatus)) {
                log.warn("Cloudinary destroy trả kết quả lạ publicId={} result={}", publicId, resultStatus);
            }
        } catch (IOException ex) {
            log.error("IOException khi xóa ảnh publicId={}", publicId, ex);
            // Không throw — chấp nhận orphan trên Cloudinary cho DATN
        } catch (RuntimeException ex) {
            log.error("RuntimeException khi xóa ảnh publicId={}: {}", publicId, ex.getMessage(), ex);
        }
    }
}
