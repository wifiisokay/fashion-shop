package com.fashionshop.backend.module.review.dto.response;

import com.fashionshop.backend.domain.Review;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewResponse {
    private Long id;
    private Integer rating;
    private String comment;
    private String customerName;
    private Boolean editable;
    private LocalDateTime createdAt;

    // Thông tin sản phẩm (dùng cho "review của tôi" và "thẻ review")
    private Long productId;
    private String productName;
    private String productImage;
    private String colorName;
    private String size;

    public static ReviewResponse from(Review review) {
        return ReviewResponse.builder()
            .id(review.getId())
            .rating(review.getRating())
            .comment(review.getComment())
            .customerName(review.getUser().getFullName())
            .editable(review.isEditable())
            .createdAt(review.getCreatedAt())
            .productId(review.getProduct() != null ? review.getProduct().getId() : null)
            .productName(review.getOrderItem() != null ? review.getOrderItem().getProductName() : null)
            .productImage(review.getOrderItem() != null ? review.getOrderItem().getImageUrl() : null)
            .colorName(review.getOrderItem() != null ? review.getOrderItem().getColorName() : null)
            .size(review.getOrderItem() != null ? review.getOrderItem().getSize() : null)
            .build();
    }
}
