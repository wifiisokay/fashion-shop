package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

import static com.fashionshop.backend.common.enums.ReturnStatus.*;

/**
 * Quản lý luồng trạng thái yêu cầu trả hàng.
 * PENDING → APPROVED → RECEIVED → COMPLETED
 *           ↘ REJECTED
 */
@Service
public class ReturnStatusService {

    private static final Map<ReturnStatus, Set<ReturnStatus>> ALLOWED_TRANSITIONS = Map.of(
        PENDING,  Set.of(APPROVED, REJECTED),
        APPROVED, Set.of(RECEIVED),
        RECEIVED, Set.of(COMPLETED)
    );

    private static final Map<ReturnStatus, String> STATUS_LABELS = Map.of(
        PENDING,   "Chờ xử lý",
        APPROVED,  "Đã duyệt",
        REJECTED,  "Từ chối",
        RECEIVED,  "Đã nhận hàng",
        COMPLETED, "Hoàn tất"
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
