package com.fashionshop.backend.module.returnrequest;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fashionshop.backend.common.enums.ReturnStatus;
import static com.fashionshop.backend.common.enums.ReturnStatus.APPROVED;
import static com.fashionshop.backend.common.enums.ReturnStatus.COMPLETED;
import static com.fashionshop.backend.common.enums.ReturnStatus.RECEIVED;
import static com.fashionshop.backend.common.enums.ReturnStatus.REJECTED;
import static com.fashionshop.backend.common.enums.ReturnStatus.REQUESTED;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;

/**
 * Quản lý luồng trạng thái yêu cầu trả hàng.
 * REQUESTED → APPROVED → RECEIVED → COMPLETED
 *             ↘ REJECTED
 */
@Service
public class ReturnStatusService {

    private static final Map<ReturnStatus, Set<ReturnStatus>> ALLOWED_TRANSITIONS = Map.of(
        REQUESTED, Set.of(APPROVED, REJECTED),
        APPROVED, Set.of(RECEIVED),
        RECEIVED, Set.of(COMPLETED)
    );

    private static final Map<ReturnStatus, String> STATUS_LABELS = Map.of(
        REQUESTED, "Chờ xử lý",
        APPROVED, "Đã duyệt",
        REJECTED, "Từ chối",
        RECEIVED, "Đã nhận hàng",
        COMPLETED, "Đã hoàn tiền"
    );

    public void validateTransition(ReturnStatus from, ReturnStatus to) {
        Set<ReturnStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException(
                ErrorCode.RETURN_INVALID_STATUS,
                HttpStatus.BAD_REQUEST,
                "Không thể chuyển từ " + getLabel(from) + " sang " + getLabel(to)
            );
        }
    }

    public String getLabel(ReturnStatus status) {
        return STATUS_LABELS.getOrDefault(status, status.name());
    }
}
