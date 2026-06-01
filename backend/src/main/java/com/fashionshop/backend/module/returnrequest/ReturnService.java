package com.fashionshop.backend.module.returnrequest;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.module.returnrequest.dto.request.CreateReturnRequest;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnDashboardResponse;
import com.fashionshop.backend.module.returnrequest.dto.response.ReturnResponse;

public interface ReturnService {

    // ====== Customer ======
    ReturnResponse createReturn(Long userId, CreateReturnRequest request);

    PageResponse<ReturnResponse> getMyReturns(Long userId, int page, int size);

    ReturnResponse getMyReturnById(Long userId, Long returnId);

    // ====== Staff / Admin ======
    PageResponse<ReturnResponse> getAllReturns(ReturnStatus status, int page, int size);

    ReturnResponse getReturnById(Long returnId);

    void approveReturn(Long staffId, Long returnId, String note);

    void rejectReturn(Long staffId, Long returnId, String note);

    // ====== Admin only ======
    void markReceived(Long adminId, Long returnId);

    void completeReturn(Long adminId, Long returnId, String note);

    ReturnDashboardResponse getAdminDashboard();

    ReturnDashboardResponse getStaffDashboard();

    String uploadEvidenceImage(org.springframework.web.multipart.MultipartFile file);
}
