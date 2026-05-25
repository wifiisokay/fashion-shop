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
    VARIANT_DUPLICATED("PRODUCT_004", "Đã tồn tại biến thể cùng màu và size"),
    INVALID_SALE_PRICE("PRODUCT_005", "Giá khuyến mãi phải nhỏ hơn giá gốc"),
    IMAGE_UPLOAD_FAILED("PRODUCT_006", "Upload ảnh thất bại"),
    IMAGE_LIMIT_EXCEEDED("PRODUCT_007", "Màu này đã đạt giới hạn 5 ảnh tối đa"),
    COLOR_NOT_FOUND("PRODUCT_008", "Không tìm thấy màu sắc này"),
    COLOR_NOT_BELONG("PRODUCT_009", "Màu không thuộc sản phẩm này"),
    PRIMARY_IMAGE_REQUIRED("PRODUCT_010", "Sản phẩm chưa có ảnh thẻ chính"),
    IMAGE_NOT_FOUND("PRODUCT_011", "Không tìm thấy ảnh"),
    INVALID_TAG("PRODUCT_012", "Tag không hợp lệ hoặc vượt giới hạn cho phép"),
    INVALID_FIELD_VALUE("PRODUCT_013", "Giá trị trường không nằm trong danh sách hợp lệ"),

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
    PAYMENT_NOT_FOUND("PAYMENT_003", "Không tìm thấy giao dịch thanh toán"),
    PAYMENT_INVALID_SIGNATURE("PAYMENT_004", "Chữ ký VNPay không hợp lệ"),
    PAYMENT_ALREADY_PROCESSED("PAYMENT_005", "Giao dịch đã được xử lý"),
    PAYMENT_INVALID_AMOUNT("PAYMENT_006", "Số tiền thanh toán không khớp"),
    PAYMENT_ORDER_NOT_FOUND("PAYMENT_007", "Không tìm thấy đơn hàng cho giao dịch này"),
    REFUND_NOT_ELIGIBLE("PAYMENT_008", "Không đủ điều kiện hoàn tiền"),
    PAYMENT_CREATION_FAILED("PAYMENT_009", "Không thể tạo giao dịch thanh toán"),

    // FILE
    INVALID_FILE_TYPE("FILE_001", "Loại file không hợp lệ"),
    FILE_TOO_LARGE("FILE_002", "File quá lớn (tối đa 5MB)"),

    // AI
    GEMINI_ERROR("AI_001", "Lỗi AI service"),

    // GENERAL
    VALIDATION_ERROR("VALIDATION_001", "Dữ liệu không hợp lệ"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_001", "Quá nhiều yêu cầu, vui lòng thử lại sau"),
    INTERNAL_ERROR("SYSTEM_001", "Lỗi hệ thống"),

    // CART
    CART_ITEM_NOT_FOUND("CART_001", "Không tìm thấy sản phẩm trong giỏ hàng"),
    INSUFFICIENT_STOCK ("CART_002", "Không đủ số lượng trong kho"),
    CART_EMPTY         ("CART_003", "Giỏ hàng trống"),
    INVALID_QUANTITY   ("CART_004", "Số lượng phải từ 1 đến 99"),
    VARIANT_IN_CART    ("CART_005", "Biến thể đang có trong giỏ hàng của người dùng, không thể xóa"),

    // ADDRESS
    ADDRESS_NOT_FOUND("ADDRESS_001", "Địa chỉ không tồn tại"),

    // SHIPPING
    SHIPPING_SERVICE_UNAVAILABLE("SHIPPING_001", "Không có dịch vụ vận chuyển khả dụng cho tuyến này"),
    ADDRESS_NOT_BELONG_TO_USER  ("SHIPPING_002", "Địa chỉ không thuộc về người dùng"),

    // ORDER (bổ sung)
    ORDER_NOT_BELONG_TO_USER("ORDER_004", "Đơn hàng không thuộc về bạn"),
    ORDER_CANCEL_REASON_REQUIRED("ORDER_005", "Vui lòng nhập lý do hủy đơn"),
    INVALID_STATUS_TRANSITION("ORDER_006", "Không thể chuyển trạng thái đơn hàng"),
    ORDER_ITEMS_EMPTY("ORDER_007", "Đơn hàng phải có ít nhất 1 sản phẩm"),
    INVALID_SHIPPING_FEE("ORDER_008", "Phí vận chuyển không hợp lệ"),
    PACKING_NOT_CONFIRMED("ORDER_009", "Vui lòng xác nhận đóng gói trước khi chuyển sang giao hàng"),
    PACKING_INVALID_STATUS("ORDER_010", "Chỉ có thể đóng gói khi đơn đã được xác nhận"),
    MAX_ITEMS_EXCEEDED("ORDER_011", "Vượt quá số lượng sản phẩm tối đa cho mỗi đơn hàng"),
    MAX_QUANTITY_EXCEEDED("ORDER_012", "Vượt quá số lượng tối đa cho mỗi sản phẩm"),
    DUPLICATE_ORDER("ORDER_013", "Vui lòng đợi trước khi đặt đơn tiếp"),
    ORDER_PAYMENT_NOT_PAID("ORDER_015", "Đơn VNPay chưa thanh toán thành công, không thể xử lý đơn."),

    // RETURN / REVIEW
    RETURN_NOT_FOUND("RETURN_001", "Yêu cầu đổi/trả hoặc khiếu nại không tồn tại"),
    RETURN_NOT_ELIGIBLE("RETURN_002", "Đơn hàng chưa đủ điều kiện tạo yêu cầu đổi/trả hoặc khiếu nại"),
    RETURN_WINDOW_EXPIRED("RETURN_003", "Đã quá 7 ngày kể từ khi giao hàng, không thể tạo yêu cầu đổi/trả hoặc khiếu nại"),
    RETURN_ALREADY_EXISTS("RETURN_004", "Đã có yêu cầu đổi/trả hoặc khiếu nại đang xử lý cho đơn này"),
    RETURN_INVALID_STATUS("RETURN_005", "Không thể chuyển trạng thái yêu cầu đổi/trả hoặc khiếu nại"),
    RETURN_REJECT_NOTE_REQUIRED("RETURN_006", "Vui lòng nhập lý do từ chối"),
    INVALID_REFUND_AMOUNT("RETURN_007", "Số tiền hoàn không hợp lệ"),
    RETURN_ITEM_NOT_IN_ORDER("RETURN_008", "Sản phẩm yêu cầu xử lý không thuộc đơn hàng này"),
    RETURN_QUANTITY_EXCEEDED("RETURN_009", "Số lượng yêu cầu xử lý vượt quá số lượng có thể đổi/trả"),
    RETURN_ITEM_REQUIRED("RETURN_010", "Vui lòng chọn ít nhất một sản phẩm cần xử lý"),
    RETURN_EXCHANGE_REFUND_NOT_ALLOWED("RETURN_011", "Yêu cầu đổi hàng chưa hỗ trợ ghi nhận hoàn tiền trong giai đoạn này"),
    RETURN_EXCHANGE_NOTE_REQUIRED("RETURN_012", "Vui lòng nhập ghi chú mô tả kết quả đổi hàng"),

    REVIEW_NOT_FOUND("REVIEW_001", "Đánh giá không tồn tại"),
    REVIEW_ALREADY_EXISTS("REVIEW_002", "Bạn đã đánh giá sản phẩm này"),
    REVIEW_NOT_ELIGIBLE("REVIEW_003", "Chỉ đánh giá được đơn đã giao"),
    REVIEW_INVALID_RATING("REVIEW_004", "Số sao phải từ 1 đến 5"),
    REVIEW_COMMENT_TOO_LONG("REVIEW_005", "Nội dung đánh giá tối đa 1000 ký tự"),
    REVIEW_EDIT_EXPIRED("REVIEW_006", "Đã quá 7 ngày, không thể sửa/xóa đánh giá"),
    ORDER_ITEM_NOT_FOUND("ORDER_014", "Sản phẩm trong đơn hàng không tồn tại");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }
}
