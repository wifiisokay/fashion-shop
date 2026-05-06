package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnResponse;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

public interface ReturnService {

    // ====== Customer ======
    ReturnResponse createReturn(Long userId, Long orderId, String reason, List<MultipartFile> images);

    PageResponse<ReturnResponse> getMyReturns(Long userId, int page, int size);

    ReturnResponse getMyReturnById(Long userId, Long returnId);

    // ====== Staff / Admin ======
    PageResponse<ReturnResponse> getAllReturns(ReturnStatus status, int page, int size);

    ReturnResponse getReturnById(Long returnId);

    void approveReturn(Long staffId, Long returnId, String note);

    void rejectReturn(Long staffId, Long returnId, String note);

    // ====== Admin only ======
    void receiveReturn(Long adminId, Long returnId);

    void completeReturn(Long adminId, Long returnId, BigDecimal refundAmount);
}
