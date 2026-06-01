package com.fashionshop.backend.module.returnrequest.dto.response;

import com.fashionshop.backend.domain.ReturnRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Getter
@Builder
public class ReturnResponse {

    private Long id;
    private Long orderId;
    private String orderCode;
    private String customerName;
    private String reason;
    private List<String> evidenceImages;
    private String status;
    private String statusLabel;
    private BigDecimal refundAmount;
    private String adminNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime refundedAt;
    private Integer remainingReturnDays;
    private List<ReturnItemResponse> items;

    public static ReturnResponse from(ReturnRequest r, String statusLabel) {
        Long orderId = null;
        String orderCode = "";
        try {
            if (r.getOrder() != null) {
                orderId = r.getOrder().getId();
                orderCode = String.valueOf(orderId);
            }
        } catch (Exception e) {
            // order not found or lazy load failed
        }

        String customerName = "";
        try {
            if (r.getUser() != null) {
                customerName = r.getUser().getFullName();
            }
        } catch (Exception e) {
            // user not found or lazy load failed
        }

        List<ReturnItemResponse> itemsList = List.of();
        try {
            if (r.getItems() != null) {
                itemsList = r.getItems().stream()
                    .map(ReturnItemResponse::from)
                    .toList();
            }
        } catch (Exception e) {
            // lazy load items failed
        }

        return ReturnResponse.builder()
            .id(r.getId())
            .orderId(orderId)
            .orderCode(orderCode)
            .customerName(customerName)
            .reason(r.getReason())
            .evidenceImages(r.getEvidenceImages())
            .status(r.getStatus() != null ? r.getStatus().name() : null)
            .statusLabel(statusLabel)
            .refundAmount(r.getRefundAmount())
            .adminNote(r.getAdminNote())
            .createdAt(r.getCreatedAt())
            .updatedAt(r.getUpdatedAt())
            .receivedAt(r.getReceivedAt())
            .refundedAt(r.getRefundedAt())
            .remainingReturnDays(calculateRemainingDays(r))
            .items(itemsList)
            .build();
    }

    private static Integer calculateRemainingDays(ReturnRequest r) {
        try {
            if (r.getOrder() == null || r.getOrder().getCompletedAt() == null) {
                return 0;
            }
            LocalDate completedDate = r.getOrder().getCompletedAt().toLocalDate();
            long daysSince = ChronoUnit.DAYS.between(completedDate, LocalDate.now());
            long remaining = 7 - daysSince;
            return (int) Math.max(0, remaining);
        } catch (Exception e) {
            return 0;
        }
    }
}
