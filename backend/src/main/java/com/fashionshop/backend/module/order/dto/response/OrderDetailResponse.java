package com.fashionshop.backend.module.order.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.Review;

import lombok.Builder;
import lombok.Getter;

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
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDate expectedDeliveryDate;

    private String paymentStatus;
    private LocalDateTime paidAt;

    private Integer packageLength;
    private Integer packageWidth;
    private Integer packageHeight;
    private Integer actualWeight;
    private Boolean packingConfirmed;
    private BigDecimal estimatedShippingFee;
    private BigDecimal shippingFeeDifference;
    private Boolean shippingFeeFallback;
    @Builder.Default
    private List<String> packingWarnings = new ArrayList<>();

    private Long returnId;
    private String returnStatus;
    private String returnStatusLabel;
    private String returnReason;
    private String returnAdminNote;
    @Builder.Default
    private List<String> returnEvidenceImages = new ArrayList<>();
    private BigDecimal returnRefundAmount;

    public static OrderDetailResponse from(Order order, String statusLabel) {
        return from(order, statusLabel, Map.of(), null, null, null, null, null, null, null,
            null, null, null);
    }

    public static OrderDetailResponse from(Order order, String statusLabel,
                                           Map<Long, Review> itemIdToReview) {
        return from(order, statusLabel, itemIdToReview, null, null, null, null, null, null, null,
            null, null, null);
    }

    public static OrderDetailResponse from(Order order, String statusLabel,
                                           Map<Long, Review> itemIdToReview,
                                           Long returnId, String returnStatus, String returnStatusLabel,
                                           String returnReason, String returnAdminNote,
                                           List<String> returnEvidenceImages, BigDecimal returnRefundAmount,
                                           BigDecimal estimatedShippingFee, BigDecimal shippingFeeDifference,
                                           List<String> packingWarnings) {
        return from(order, statusLabel, itemIdToReview, returnId, returnStatus, returnStatusLabel, returnReason,
            returnAdminNote, returnEvidenceImages, returnRefundAmount, estimatedShippingFee, shippingFeeDifference,
            packingWarnings, null, Set.of());
    }

    public static OrderDetailResponse from(Order order, String statusLabel,
                                           Map<Long, Review> itemIdToReview,
                                           Long returnId, String returnStatus, String returnStatusLabel,
                                           String returnReason, String returnAdminNote,
                                           List<String> returnEvidenceImages, BigDecimal returnRefundAmount,
                                           BigDecimal estimatedShippingFee, BigDecimal shippingFeeDifference,
                                           List<String> packingWarnings, Boolean shippingFeeFallback) {
        return from(order, statusLabel, itemIdToReview, returnId, returnStatus, returnStatusLabel, returnReason,
            returnAdminNote, returnEvidenceImages, returnRefundAmount, estimatedShippingFee, shippingFeeDifference,
            packingWarnings, shippingFeeFallback, Set.of());
    }

    public static OrderDetailResponse from(Order order, String statusLabel,
                                           Map<Long, Review> itemIdToReview,
                                           Long returnId, String returnStatus, String returnStatusLabel,
                                           String returnReason, String returnAdminNote,
                                           List<String> returnEvidenceImages, BigDecimal returnRefundAmount,
                                           BigDecimal estimatedShippingFee, BigDecimal shippingFeeDifference,
                                           List<String> packingWarnings, Boolean shippingFeeFallback,
                                           Set<Long> nonReviewableReturnItemIds) {
        boolean canReviewOrder = order.getStatus() == OrderStatus.COMPLETED
            && order.getPaymentStatus() == OrderPaymentStatus.PAID;
        Set<Long> blockedItemIds = nonReviewableReturnItemIds != null ? nonReviewableReturnItemIds : Set.of();

        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(item -> {
                Review review = itemIdToReview.get(item.getId());
                boolean reviewed = review != null;
                Boolean canReview = canReviewOrder && !reviewed && !blockedItemIds.contains(item.getId()) ? true : null;
                Long reviewId = reviewed ? review.getId() : null;
                Integer reviewRating = reviewed ? review.getRating() : null;
                String reviewComment = reviewed ? review.getComment() : null;
                return OrderItemResponse.from(item, canReview, reviewId, reviewRating, reviewComment);
            })
            .toList();

        List<String> warnings = packingWarnings != null
            ? packingWarnings
            : buildPackingWarnings(order, estimatedShippingFee, shippingFeeDifference);

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
            .completedAt(order.getCompletedAt())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .expectedDeliveryDate(order.getExpectedDeliveryDate())
            .packageLength(order.getPackageLength())
            .packageWidth(order.getPackageWidth())
            .packageHeight(order.getPackageHeight())
            .actualWeight(order.getActualWeight())
            .packingConfirmed(order.getPackingConfirmed())
            .estimatedShippingFee(estimatedShippingFee)
            .shippingFeeDifference(shippingFeeDifference)
            .shippingFeeFallback(shippingFeeFallback)
            .packingWarnings(warnings)
            .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
            .paidAt(order.getPayment() != null ? order.getPayment().getPaidAt() : null)
            .returnId(returnId)
            .returnStatus(returnStatus)
            .returnStatusLabel(returnStatusLabel)
            .returnReason(returnReason)
            .returnAdminNote(returnAdminNote)
            .returnEvidenceImages(returnEvidenceImages != null ? returnEvidenceImages : new ArrayList<>())
            .returnRefundAmount(returnRefundAmount)
            .build();
    }

    private static List<String> buildPackingWarnings(Order order,
                                                     BigDecimal estimatedShippingFee,
                                                     BigDecimal shippingFeeDifference) {
        List<String> warnings = new ArrayList<>();
        if (order.getPackageLength() == null) {
            return warnings;
        }

        if (order.getPackageLength() > 30 || order.getPackageWidth() > 30 || order.getPackageHeight() > 30) {
            warnings.add("Co chieu > 30cm, GHN co the can do lai khi lay hang");
        }

        if (estimatedShippingFee != null && shippingFeeDifference != null) {
            int cmp = shippingFeeDifference.compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                warnings.add("Phi ship GHN theo kien that cao hon phi khach da tra.");
            } else if (cmp < 0) {
                warnings.add("Phi ship GHN theo kien that thap hon phi khach da tra.");
            }
        }

        return warnings;
    }
}
