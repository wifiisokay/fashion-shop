package com.fashionshop.backend.module.storage;

/**
 * Kết quả upload file lên cloud storage.
 * Dùng chung cho mọi loại upload (avatar, product image, ...).
 */
public record UploadResult(String url, String publicId) {}
