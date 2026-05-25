package com.fashionshop.backend.module.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {

    private DashboardDateRange dateRange;
    private OverviewSummary overview;
    private RevenueSummary revenue;
    private OrderSummary orders;
    private ReturnSummary returns;
    private ProductAnalytics products;
    private DashboardCharts charts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardDateRange {
        private LocalDate from;
        private LocalDate to;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewSummary {
        private BigDecimal netRevenue;
        private BigDecimal finalizedGrossRevenue;
        private BigDecimal processedRefundAmount;
        private BigDecimal pendingRevenue;
        private long finalizedOrderCount;
        private long pendingOrderCount;
        private long shippingOrderCount;
        private long pendingReturnCount;
        private long lowStockProductCount;
        private long activeProductCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueSummary {
        private BigDecimal finalizedGrossRevenue;
        private BigDecimal processedRefundAmount;
        private BigDecimal netRevenue;
        private BigDecimal codRevenue;
        private BigDecimal vnpayRevenue;
        private long finalizedOrderCount;
        private BigDecimal pendingRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        private Map<String, Long> orderStatusDistribution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnSummary {
        private long pendingReturns;
        private long processingReturns;
        private long rejectedReturns;
        private long completedReturns;
        private long pendingOver24h;
        private long approvedOver3Days;
        private long receivedNotCompleted;
        private BigDecimal processedRefundAmount;
        private long returnItemQuantity;
        private BigDecimal returnItemValue;
        private List<ReturnQueueItem> queue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAnalytics {
        private List<ProductMetric> topSellingProducts;
        private List<ProductMetric> topRevenueProducts;
        private List<ReturnedProductMetric> topReturnedProducts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardCharts {
        private List<DailyRevenuePoint> dailyRevenue;
        private List<RevenueByMethodPoint> paymentMethodRevenue;
        private Map<String, Long> orderStatusDistribution;
        private Map<String, Long> returnStatusDistribution;
        private Map<String, Long> returnTypeDistribution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenuePoint {
        private String date;
        private BigDecimal revenue;
        private long orderCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueByMethodPoint {
        private String paymentMethod;
        private BigDecimal revenue;
        private long orderCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductMetric {
        private Long productId;
        private String productName;
        private long quantity;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnedProductMetric {
        private Long productId;
        private String productName;
        private long returnedQuantity;
        private BigDecimal returnedValue;
        private long requestCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnQueueItem {
        private Long returnId;
        private Long orderId;
        private String customerName;
        private String requestTypeLabel;
        private String status;
        private LocalDateTime createdAt;
    }
}
