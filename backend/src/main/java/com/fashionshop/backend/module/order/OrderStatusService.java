package com.fashionshop.backend.module.order;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

import static com.fashionshop.backend.common.enums.OrderStatus.*;

/**
 * Quản lý luồng trạng thái đơn hàng.
 * Dùng Map thay vì if-else chain — dễ mở rộng.
 */
@Service
public class OrderStatusService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
        AWAITING_PAYMENT, Set.of(PENDING, CANCELLED),
        PENDING,          Set.of(CONFIRMED, CANCELLED),
        CONFIRMED,        Set.of(SHIPPING, CANCELLED),
        SHIPPING,         Set.of(DELIVERED),
        DELIVERED,        Set.of(COMPLETED, RETURN_REQUESTED)
    );

    private static final Map<OrderStatus, String> STATUS_LABELS = Map.ofEntries(
        Map.entry(AWAITING_PAYMENT, "Chờ thanh toán"),
        Map.entry(PENDING,          "Chờ xác nhận"),
        Map.entry(CONFIRMED,        "Đã xác nhận"),
        Map.entry(SHIPPING,         "Đang giao hàng"),
        Map.entry(DELIVERED,        "Đã giao hàng"),
        Map.entry(COMPLETED,        "Hoàn thành"),
        Map.entry(CANCELLED,        "Đã hủy"),
        Map.entry(RETURN_REQUESTED, "Yêu cầu trả hàng"),
        Map.entry(RETURNING,        "Đang trả hàng"),
        Map.entry(RETURNED,         "Đã trả hàng")
    );

    /** Validate chuyển trạng thái hợp lệ — throw nếu không hợp lệ. */
    public void validateTransition(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException(
                ErrorCode.INVALID_STATUS_TRANSITION,
                HttpStatus.BAD_REQUEST,
                "Không thể chuyển từ " + getLabel(from) + " sang " + getLabel(to)
            );
        }
    }

    /** Lấy label tiếng Việt cho status. */
    public String getLabel(OrderStatus status) {
        return STATUS_LABELS.getOrDefault(status, status.name());
    }

    /** Customer chỉ hủy được: AWAITING_PAYMENT, PENDING */
    public boolean canCustomerCancel(OrderStatus status) {
        return status == AWAITING_PAYMENT || status == PENDING;
    }

    /** Staff hủy được: AWAITING_PAYMENT, PENDING, CONFIRMED */
    public boolean canStaffCancel(OrderStatus status) {
        return status == AWAITING_PAYMENT || status == PENDING || status == CONFIRMED;
    }
}
