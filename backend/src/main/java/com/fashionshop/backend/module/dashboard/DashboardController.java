package com.fashionshop.backend.module.dashboard;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DashboardStatsResponse> getStats() {
        return ApiResponse.success(dashboardService.getDashboardStats());
    }
}
