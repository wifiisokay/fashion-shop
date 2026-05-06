package com.fashionshop.backend.module.review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewStatResponse {
    private Long productId;
    private String productName;
    private String productImage;
    private Double avgRating;
    private Long totalReviews;
}
