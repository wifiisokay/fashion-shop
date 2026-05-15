package com.fashionshop.backend.module.ai.dto.response;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStatsResponse {

    /** Phân bổ intent: { "PRODUCT_SEARCH": 45, "ORDER_INQUIRY": 20, ... } */
    private Map<String, Long> intentDistribution;

    /** Số message theo ngày: { "2026-05-01": 12, "2026-05-02": 8, ... } */
    private Map<String, Long> messagesPerDay;

    private long totalMessages;
    private long totalSessions;
}
