package com.fashionshop.backend.module.returnrequest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnDashboardResponse {

    private Summary summary;
    private Alerts alerts;
    private List<ChartPoint> statusChart;
    private List<ChartPoint> typeChart;
    private List<ReturnResponse> queue;
    private List<TopReturnedProduct> topReturnedProducts;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long pending;
        private long processing;
        private long rejectedThisMonth;
        private long completedThisMonth;
        private BigDecimal processedRefundAmountThisMonth;
        private long returnItemQuantityThisMonth;
        private BigDecimal returnItemValueThisMonth;
        private long pendingOver24h;
        private long approvedToday;
        private long rejectedToday;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alerts {
        private long pendingOver24h;
        private long approvedOver3Days;
        private long receivedNotCompleted;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartPoint {
        private String label;
        private long value;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopReturnedProduct {
        private Long productId;
        private String productName;
        private long returnedQuantity;
        private BigDecimal returnedValue;
        private long requestCount;
    }
}
