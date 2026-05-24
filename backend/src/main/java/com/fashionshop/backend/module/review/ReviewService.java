package com.fashionshop.backend.module.review;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.module.review.dto.request.CreateReviewRequest;
import com.fashionshop.backend.module.review.dto.request.UpdateReviewRequest;
import com.fashionshop.backend.module.review.dto.response.ReviewResponse;
import com.fashionshop.backend.module.review.dto.response.ReviewStatsResponse;

public interface ReviewService {

    ReviewResponse createReview(Long userId, CreateReviewRequest request);

    ReviewResponse updateReview(Long userId, Long reviewId, UpdateReviewRequest request);

    void deleteReview(Long userId, Long reviewId);

    PageResponse<ReviewResponse> getProductReviews(Long productId, Integer rating, int page, int size, String sort);

    ReviewStatsResponse getReviewStats(Long productId);

    PageResponse<ReviewResponse> getMyReviews(Long userId, int page, int size);

    // Admin
    PageResponse<ReviewResponse> getAllReviews(Long productId, int page, int size);

    // 8. Admin: Thống kê đánh giá theo sản phẩm
    PageResponse<com.fashionshop.backend.module.review.dto.response.ProductReviewStatResponse> getProductReviewStats(Integer categoryId, int page, int size);
}
