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
    private String requestTypeLabel;
    private List<ReturnItemResponse> items;
    private List<ReturnItemResponse> orderItems;
    private Integer totalReturnQuantity;
    private BigDecimal totalReturnValue;
    private List<String> evidenceImages;
    private String status;
    private String statusLabel;
    private String previousOrderStatus;
    private BigDecimal refundAmount;
    private String adminNote;
    private String processedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReturnResponse from(ReturnRequest r, String statusLabel) {
        List<ReturnItemResponse> itemResponses = r.getItems().stream()
            .map(ReturnItemResponse::from)
            .toList();
        Integer totalQuantity = itemResponses.stream()
            .map(ReturnItemResponse::getQuantity)
            .filter(qty -> qty != null)
            .reduce(0, Integer::sum);
        BigDecimal totalValue = itemResponses.stream()
            .map(ReturnItemResponse::getSubtotal)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ReturnResponse.builder()
            .id(r.getId())
            .orderId(r.getOrder().getId())
            .customerName(r.getUser().getFullName())
            .reason(stripReasonPrefix(r.getReason()))
            .requestTypeLabel(resolveRequestTypeLabel(r.getReason()))
            .items(itemResponses)
            .orderItems(itemResponses)
            .totalReturnQuantity(totalQuantity)
            .totalReturnValue(totalValue)
            .evidenceImages(r.getEvidenceImages())
            .status(r.getStatus().name())
            .statusLabel(statusLabel)
            .previousOrderStatus(r.getPreviousOrderStatus())
            .refundAmount(r.getRefundAmount())
            .adminNote(r.getAdminNote())
            .processedByName(r.getProcessedBy() != null ? r.getProcessedBy().getFullName() : null)
            .createdAt(r.getCreatedAt())
            .updatedAt(r.getUpdatedAt())
            .build();
    }

    private static String resolveRequestTypeLabel(String reason) {
        if (reason == null) return "Trả hàng";
        if (reason.startsWith("[ĐỔI HÀNG]")) return "Đổi hàng";
        if (reason.startsWith("[KHIẾU NẠI]")) return "Khiếu nại";
        return "Trả hàng";
    }

    private static String stripReasonPrefix(String reason) {
        if (reason == null) return null;
        return reason.replaceFirst("^\\[(TRẢ HÀNG|ĐỔI HÀNG|KHIẾU NẠI)]\\s*", "");
    }
}
