package com.fashionshop.backend.exception;

/**
 * Tất cả error codes của hệ thống — khớp với API_CONVENTIONS.md
 */
public enum ErrorCode {
    // AUTH
    USER_NOT_FOUND("AUTH_001", "Không tìm thấy người dùng"),
    EMAIL_ALREADY_EXISTS("AUTH_002", "Email đã được sử dụng"),
    INVALID_CREDENTIALS("AUTH_003", "Email hoặc mật khẩu không đúng"),
    ACCOUNT_LOCKED("AUTH_004", "Tài khoản đã bị khóa"),
    FORBIDDEN("AUTH_005", "Không có quyền truy cập"),
    RESET_TOKEN_INVALID("AUTH_006", "Reset token không hợp lệ hoặc đã hết hạn"),
    PASSWORD_REUSE_NOT_ALLOWED("AUTH_007", "Mật khẩu mới không được trùng mật khẩu hiện tại"),

    // PRODUCT
    PRODUCT_NOT_FOUND("PRODUCT_001", "Sản phẩm không tồn tại"),
    PRODUCT_OUT_OF_STOCK("PRODUCT_002", "Sản phẩm đã hết hàng"),
    VARIANT_NOT_FOUND("PRODUCT_003", "Biến thể sản phẩm không tồn tại"),

    // CATEGORY
    CATEGORY_NOT_FOUND("CATEGORY_001", "Danh mục không tồn tại"),
    CATEGORY_SLUG_EXISTS("CATEGORY_002", "Slug danh mục đã tồn tại"),
    CATEGORY_INVALID_PARENT("CATEGORY_003", "Danh mục cha không hợp lệ"),
    CATEGORY_HAS_ACTIVE_PRODUCTS("CATEGORY_004", "Danh mục đang có sản phẩm đang hoạt động"),
    CATEGORY_HAS_CHILDREN("CATEGORY_005", "Danh mục đang có danh mục con"),

    // ORDER
    ORDER_NOT_FOUND("ORDER_001", "Đơn hàng không tồn tại"),
    ORDER_CANNOT_CANCEL("ORDER_002", "Không thể huỷ đơn hàng ở trạng thái này"),
    ORDER_ALREADY_PAID("ORDER_003", "Đơn hàng đã được thanh toán"),

    // PAYMENT
    PAYMENT_VERIFY_FAILED("PAYMENT_001", "Xác thực thanh toán thất bại"),
    PAYMENT_REFUND_FAILED("PAYMENT_002", "Hoàn tiền thất bại"),

    // FILE
    INVALID_FILE_TYPE("FILE_001", "Loại file không hợp lệ"),
    FILE_TOO_LARGE("FILE_002", "File quá lớn (tối đa 5MB)"),

    // AI
    GEMINI_ERROR("AI_001", "Lỗi AI service"),

    // GENERAL
    VALIDATION_ERROR("VALIDATION_001", "Dữ liệu không hợp lệ"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_001", "Quá nhiều yêu cầu, vui lòng thử lại sau"),
    INTERNAL_ERROR("SYSTEM_001", "Lỗi hệ thống"),

    // ADDRESS
    ADDRESS_NOT_FOUND("ADDRESS_001", "Địa chỉ không tồn tại"),

    // RETURN / REVIEW
    RETURN_NOT_FOUND("RETURN_001", "Yêu cầu trả hàng không tồn tại"),
    REVIEW_NOT_FOUND("REVIEW_001", "Đánh giá không tồn tại"),
    REVIEW_ALREADY_EXISTS("REVIEW_002", "Bạn đã đánh giá sản phẩm này");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
}
