package com.fashionshop.backend.module.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatsResponse {
    private long totalOrders;
    private long pendingCount;
    private long confirmedCount;
    private long shippingCount;
    private long deliveredCount;
    private long completedCount;
    private long cancelledCount;
    private long returnCount;
}
