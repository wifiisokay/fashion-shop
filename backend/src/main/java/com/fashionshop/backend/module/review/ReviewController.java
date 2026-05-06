package com.fashionshop.backend.module.review;

import com.fashionshop.backend.common.ApiResponse;
import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.module.review.dto.request.CreateReviewRequest;
import com.fashionshop.backend.module.review.dto.request.UpdateReviewRequest;
import com.fashionshop.backend.module.review.dto.response.ReviewResponse;
import com.fashionshop.backend.module.review.dto.response.ReviewStatsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Review Controller — Public xem review + Customer CRUD.
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // ================================================================
    // Public — Xem review sản phẩm
    // ================================================================

    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "newest") String sort) {
        return ResponseEntity.ok(ApiResponse.success(
            reviewService.getProductReviews(productId, rating, page, size, sort)));
    }

    @GetMapping("/api/products/{productId}/review-stats")
    public ResponseEntity<ApiResponse<ReviewStatsResponse>> getReviewStats(
            @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(
            reviewService.getReviewStats(productId)));
    }

    // ================================================================
    // Customer — CRUD review
    // ================================================================

    @PostMapping("/api/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = ((User) userDetails).getId();
        ReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Đánh giá thành công", response));
    }

    @PutMapping("/api/reviews/{id}")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = ((User) userDetails).getId();
        ReviewResponse response = reviewService.updateReview(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật đánh giá thành công", response));
    }

    @DeleteMapping("/api/reviews/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = ((User) userDetails).getId();
        reviewService.deleteReview(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Xóa đánh giá thành công", null));
    }

    @GetMapping("/api/reviews/my")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getMyReviews(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = ((User) userDetails).getId();
        return ResponseEntity.ok(ApiResponse.success(
            reviewService.getMyReviews(userId, page, size)));
    }
}
