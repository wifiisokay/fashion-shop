package com.fashionshop.backend.module.order.dto.response;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.Review;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class OrderDetailResponse {

    private Long id;
    private String status;
    private String statusLabel;
    private String paymentMethod;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String note;
    private String cancelReason;
    private Map<String, Object> addressSnapshot;
    private List<OrderItemResponse> items;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDate expectedDeliveryDate;

    // Payment
    private String paymentStatus;
    private LocalDateTime paidAt;

    // Packing
    private Integer packageLength;
    private Integer packageWidth;
    private Integer packageHeight;
    private Integer actualWeight;
    private Integer volumetricWeight;
    private Integer chargeableWeight;
    private Boolean packingConfirmed;
    @Builder.Default
    private List<String> packingWarnings = new ArrayList<>();

    // Return
    private Long returnId;
    private String returnStatus;
    private String returnStatusLabel;
    private String returnReason;
    private String returnAdminNote;
    @Builder.Default
    private List<String> returnEvidenceImages = new ArrayList<>();
    private BigDecimal returnRefundAmount;

    public static OrderDetailResponse from(Order order, String statusLabel) {
        return from(order, statusLabel, Map.of(), null, null, null, null, null, null, null);
    }

    public static OrderDetailResponse from(Order order, String statusLabel,
                                           Map<Long, Review> itemIdToReview) {
        return from(order, statusLabel, itemIdToReview, null, null, null, null, null, null, null);
    }

    public static OrderDetailResponse from(Order order, String statusLabel,
                                           Map<Long, Review> itemIdToReview,
                                           Long returnId, String returnStatus, String returnStatusLabel,
                                           String returnReason, String returnAdminNote,
                                           List<String> returnEvidenceImages, BigDecimal returnRefundAmount) {
        boolean canReviewOrder = order.getStatus() == OrderStatus.DELIVERED
            || order.getStatus() == OrderStatus.COMPLETED;

        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(item -> {
                Review review = itemIdToReview.get(item.getId());
                boolean reviewed = review != null;
                Boolean canReview = canReviewOrder && !reviewed ? true : null;
                Long reviewId = reviewed ? review.getId() : null;
                Integer reviewRating = reviewed ? review.getRating() : null;
                String reviewComment = reviewed ? review.getComment() : null;
                return OrderItemResponse.from(item, canReview, reviewId, reviewRating, reviewComment);
            })
            .toList();

        List<String> warnings = buildPackingWarnings(order);

        return OrderDetailResponse.builder()
            .id(order.getId())
            .status(order.getStatus().name())
            .statusLabel(statusLabel)
            .paymentMethod(order.getPaymentMethod().name())
            .subtotal(order.getSubtotal())
            .shippingFee(order.getShippingFee())
            .totalAmount(order.getTotalAmount())
            .note(order.getNote())
            .cancelReason(order.getCancelReason())
            .addressSnapshot(order.getAddressSnapshot())
            .items(itemResponses)
            .deliveredAt(order.getDeliveredAt())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .expectedDeliveryDate(order.getExpectedDeliveryDate())
            // Packing
            .packageLength(order.getPackageLength())
            .packageWidth(order.getPackageWidth())
            .packageHeight(order.getPackageHeight())
            .actualWeight(order.getActualWeight())
            .volumetricWeight(order.getVolumetricWeight())
            .chargeableWeight(order.getChargeableWeight())
            .packingConfirmed(order.getPackingConfirmed())
            .packingWarnings(warnings)
            // Payment info
            .paymentStatus(order.getPayment() != null ? order.getPayment().getStatus().name() : null)
            .paidAt(order.getPayment() != null ? order.getPayment().getPaidAt() : null)
            // Return info
            .returnId(returnId)
            .returnStatus(returnStatus)
            .returnStatusLabel(returnStatusLabel)
            .returnReason(returnReason)
            .returnAdminNote(returnAdminNote)
            .returnEvidenceImages(returnEvidenceImages != null ? returnEvidenceImages : new ArrayList<>())
            .returnRefundAmount(returnRefundAmount)
            .build();
    }

    /**
     * Cảnh báo nghiệp vụ cho Staff khi đóng gói.
     */
    private static List<String> buildPackingWarnings(Order order) {
        List<String> warnings = new ArrayList<>();
        if (order.getPackageLength() == null) return warnings;

        if (order.getPackageLength() > 30 || order.getPackageWidth() > 30 || order.getPackageHeight() > 30) {
            warnings.add("Có chiều > 30cm — GHN có thể cân đo lại khi lấy hàng");
        }

        if (order.getActualWeight() != null && order.getVolumetricWeight() != null) {
            int diff = Math.abs(order.getActualWeight() - order.getVolumetricWeight());
            int maxW = Math.max(order.getActualWeight(), order.getVolumetricWeight());
            if (maxW > 0 && (double) diff / maxW > 0.5) {
                warnings.add("Chênh lệch > 50% giữa cân nặng thực tế và quy đổi — có nguy cơ lệch cước");
            }
        }

        return warnings;
    }
}
