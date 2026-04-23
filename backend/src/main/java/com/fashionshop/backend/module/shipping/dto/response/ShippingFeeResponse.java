package com.fashionshop.backend.module.shipping.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Response trả về frontend với đầy đủ thông tin phí ship.
 */
@Getter
@Builder
@AllArgsConstructor
public class ShippingFeeResponse {

    /** Phí vận chuyển (VND) */
    private long fee;

    /** Số ngày giao dự kiến */
    private int estimatedDays;

    /** Text mô tả ngày giao, ví dụ: "Dự kiến giao Thứ 4, 23/04" */
    private String estimatedDateText;

    /** Tên dịch vụ GHN, ví dụ: "Giao hàng chuẩn" */
    private String serviceName;

    /** true nếu lấy từ cache */
    private boolean cached;

    /** true nếu đang dùng giá trị fallback do GHN lỗi */
    private boolean fallback;
}
