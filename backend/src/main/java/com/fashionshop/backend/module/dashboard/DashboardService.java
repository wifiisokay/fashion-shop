package com.fashionshop.backend.module.dashboard;

import com.fashionshop.backend.module.dashboard.dto.AdminDashboardResponse;
import com.fashionshop.backend.module.dashboard.dto.DashboardStatsResponse;

import java.time.LocalDate;

public interface DashboardService {
    DashboardStatsResponse getDashboardStats();

    AdminDashboardResponse getAdminDashboard(LocalDate from, LocalDate to);
}
