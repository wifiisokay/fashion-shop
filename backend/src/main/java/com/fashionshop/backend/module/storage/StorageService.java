package com.fashionshop.backend.module.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Contract cho dịch vụ lưu trữ file (avatar, ảnh sản phẩm, ...).
 * Implementation hiện tại: Cloudinary. Có thể swap sang S3/Firebase sau này.
 */
public interface StorageService {

    /**
     * Tạo publicId chuẩn cho avatar của user.
     * Format: fashion-shop/users/{userId}/avatar
     */
    String buildUserAvatarPublicId(Long userId);

    /**
     * Upload ảnh avatar lên storage và trả về secure URL.
     * File sẽ được validate (type + size) trước khi upload.
     *
     * @throws com.fashionshop.backend.exception.BusinessException nếu file không hợp lệ hoặc upload thất bại
     */
    String uploadAvatar(MultipartFile file, String publicId);

    /**
     * Xóa ảnh avatar khỏi storage theo publicId.
     * Không throw nếu file không tồn tại (Cloudinary trả "not found" — bỏ qua).
     */
    void deleteAvatar(String publicId);

    // ============ Generic image upload (Product, v.v.) ============

    /**
     * Upload ảnh lên storage vào folder chỉ định.
     * File sẽ được validate (type + size) trước khi upload.
     *
     * @param file   MultipartFile từ request
     * @param folder Folder trên Cloudinary, ví dụ "fashion-shop/products"
     * @return UploadResult chứa url và publicId
     * @throws com.fashionshop.backend.exception.BusinessException nếu file không hợp lệ hoặc upload thất bại
     */
    UploadResult uploadImage(org.springframework.web.multipart.MultipartFile file, String folder);

    /**
     * Xóa ảnh khỏi storage theo publicId.
     * Không throw nếu file không tồn tại — log warning và bỏ qua.
     */
    void deleteImage(String publicId);
}
