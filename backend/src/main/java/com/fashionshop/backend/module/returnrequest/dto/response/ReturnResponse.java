package com.fashionshop.backend.module.returnrequest.dto.response;

import com.fashionshop.backend.domain.ReturnRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ReturnResponse {

    private Long id;
    private Long orderId;
    private String customerName;
    private String reason;
    private List<String> evidenceImages;
    private String status;
    private String statusLabel;
    private BigDecimal refundAmount;
    private String adminNote;
    private String processedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReturnResponse from(ReturnRequest r, String statusLabel) {
        return ReturnResponse.builder()
            .id(r.getId())
            .orderId(r.getOrder().getId())
            .customerName(r.getUser().getFullName())
            .reason(r.getReason())
            .evidenceImages(r.getEvidenceImages())
            .status(r.getStatus().name())
            .statusLabel(statusLabel)
            .refundAmount(r.getRefundAmount())
            .adminNote(r.getAdminNote())
            .processedByName(r.getProcessedBy() != null ? r.getProcessedBy().getFullName() : null)
            .createdAt(r.getCreatedAt())
            .updatedAt(r.getUpdatedAt())
            .build();
    }
}
