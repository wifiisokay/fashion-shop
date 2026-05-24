package com.fashionshop.backend.module.storage;

/**
 * Utility để append Cloudinary transformation param vào base URL.
 * Lưu base_url (không transform) trong DB, build URL theo context khi trả response.
 *
 * URL Cloudinary format: https://res.cloudinary.com/{cloud}/image/upload/{transformations}/v{version}/{public_id}.{format}
 * Chèn transform param SAU "/upload/" và TRƯỚC phần còn lại.
 */
public final class CloudinaryUrlBuilder {

    private static final String UPLOAD_SEGMENT = "/upload/";

    // Listing card: crop 4:5 chuẩn thời trang, chất lượng auto
    private static final String LISTING_TRANSFORM = "w_400,h_500,c_fill,q_auto,f_webp";

    // Detail gallery: cùng tỉ lệ, độ phân giải cao hơn
    private static final String DETAIL_TRANSFORM = "w_800,h_1000,c_fill,q_auto,f_webp";

    // Chatbot thumbnail: nhỏ, load nhanh trong bubble chat
    private static final String CHATBOT_TRANSFORM = "w_120,h_150,c_fill,q_auto,f_webp";

    private CloudinaryUrlBuilder() {}

    public static String listing(String baseUrl) {
        return applyTransform(baseUrl, LISTING_TRANSFORM);
    }

    public static String detail(String baseUrl) {
        return applyTransform(baseUrl, DETAIL_TRANSFORM);
    }

    public static String chatbot(String baseUrl) {
        return applyTransform(baseUrl, CHATBOT_TRANSFORM);
    }

    /**
     * Chèn transformation param vào Cloudinary URL.
     * Input:  https://res.cloudinary.com/xxx/image/upload/v123/folder/file.jpg
     * Output: https://res.cloudinary.com/xxx/image/upload/w_400,h_500,.../v123/folder/file.jpg
     */
    private static String applyTransform(String baseUrl, String transform) {
        if (baseUrl == null || baseUrl.isBlank()) return null;

        int idx = baseUrl.indexOf(UPLOAD_SEGMENT);
        if (idx == -1) {
            // Không phải Cloudinary URL → trả nguyên
            return baseUrl;
        }

        int insertPos = idx + UPLOAD_SEGMENT.length();
        return baseUrl.substring(0, insertPos) + transform + "/" + baseUrl.substring(insertPos);
    }
}
