package com.fashionshop.backend.module.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    private Totals totals;
    private List<RevenuePoint> revenueTrend;
    private List<OrderStatusCount> orderStatusDistribution;
    private PackingStats packingStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Totals {
        private BigDecimal totalRevenue;
        private long totalOrders;
        private long totalCustomers;
        private long totalProducts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenuePoint {
        private String date;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusCount {
        private String status;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackingStats {
        private long confirmedNotPacked;
        private long confirmedPacked;
        private BigDecimal shippingFeeTotalThisMonth;
        private BigDecimal shippingFeeAvgThisMonth;
    }
}
