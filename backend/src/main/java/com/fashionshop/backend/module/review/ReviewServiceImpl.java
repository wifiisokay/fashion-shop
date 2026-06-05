package com.fashionshop.backend.module.review;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.domain.OrderItem;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.Review;
import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import com.fashionshop.backend.domain.repository.OrderItemRepository;
import com.fashionshop.backend.domain.repository.ProductImageRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ReviewRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.review.dto.request.CreateReviewRequest;
import com.fashionshop.backend.module.review.dto.request.UpdateReviewRequest;
import com.fashionshop.backend.module.review.dto.response.ReviewResponse;
import com.fashionshop.backend.module.review.dto.response.ReviewStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;

    // ================================================================
    // 1. Customer: tạo review
    // ================================================================

    @Override
    @Transactional
    public ReviewResponse createReview(Long userId, CreateReviewRequest request) {
        // Bước 1: Tìm OrderItem
        OrderItem orderItem = orderItemRepository.findById(request.getOrderItemId())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.ORDER_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND));

        // Bước 2: Verify chủ đơn
        if (!orderItem.getOrder().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        }

        // Bước 3: Verify đơn đã giao
        OrderStatus status = orderItem.getOrder().getStatus();
        if (status != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ELIGIBLE, HttpStatus.BAD_REQUEST);
        }

        // Bước 4: Unique check
        if (reviewRepository.existsByOrderItemId(request.getOrderItemId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }

        // Lấy product từ repository để đảm bảo không bị null
        Product product = null;
        if (orderItem.getProductId() != null) {
            product = productRepository.findById(orderItem.getProductId()).orElse(null);
        }

        // Bước 5: Save
        Review review = Review.builder()
            .orderItem(orderItem)
            .user(orderItem.getOrder().getUser())
            .product(product)
            .rating(request.getRating())
            .comment(request.getComment())
            .build();

        reviewRepository.save(review);
        log.info("Review created — userId={}, orderItemId={}, rating={}",
            userId, request.getOrderItemId(), request.getRating());

        return ReviewResponse.from(review);
    }

    // ================================================================
    // 2. Customer: sửa review (trong 7 ngày)
    // ================================================================

    @Override
    @Transactional
    public ReviewResponse updateReview(Long userId, Long reviewId, UpdateReviewRequest request) {
        Review review = findAndValidateOwnership(reviewId, userId);

        if (!review.isEditable()) {
            throw new BusinessException(ErrorCode.REVIEW_EDIT_EXPIRED, HttpStatus.BAD_REQUEST);
        }

        review.setRating(request.getRating());
        review.setComment(request.getComment());
        reviewRepository.save(review);

        log.info("Review #{} updated by user #{}", reviewId, userId);
        return ReviewResponse.from(review);
    }

    // ================================================================
    // 3. Customer: xóa review (trong 7 ngày)
    // ================================================================

    @Override
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = findAndValidateOwnership(reviewId, userId);

        if (!review.isEditable()) {
            throw new BusinessException(ErrorCode.REVIEW_EDIT_EXPIRED, HttpStatus.BAD_REQUEST);
        }

        reviewRepository.delete(review);
        log.info("Review #{} deleted by user #{}", reviewId, userId);
    }

    // ================================================================
    // 4. Public: review sản phẩm (phân trang + filter rating)
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getProductReviews(
            Long productId, Integer rating, int page, int size, String sort) {

        Sort sortOrder = switch (sort != null ? sort : "newest") {
            case "highest" -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "lowest"  -> Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default        -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        Page<Review> reviews = (rating != null)
            ? reviewRepository.findByProductIdAndRating(productId, rating, PageRequest.of(page, size, sortOrder))
            : reviewRepository.findByProductId(productId, PageRequest.of(page, size, sortOrder));

        List<ReviewResponse> content = reviews.getContent().stream()
            .map(ReviewResponse::from).toList();
        return PageResponse.from(content, reviews);
    }

    // ================================================================
    // 5. Public: thống kê rating sản phẩm
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public ReviewStatsResponse getReviewStats(Long productId) {
        List<Object[]> results = reviewRepository.getStatsByProductId(productId);
        Object[] stats = results.isEmpty() ? new Object[]{null, 0} : results.get(0);
        Double avg = stats[0] != null ? ((Number) stats[0]).doubleValue() : null;
        int total = stats[1] != null ? ((Number) stats[1]).intValue() : 0;

        // Breakdown
        Map<Integer, Integer> breakdown = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) breakdown.put(i, 0);

        reviewRepository.getBreakdownByProductId(productId).forEach(row -> {
            int star = ((Number) row[0]).intValue();
            int count = ((Number) row[1]).intValue();
            breakdown.put(star, count);
        });

        return ReviewStatsResponse.builder()
            .avgRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null)
            .totalReviews(total)
            .breakdown(breakdown)
            .build();
    }

    // ================================================================
    // 6. Customer: review của tôi
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getMyReviews(Long userId, int page, int size) {
        Page<Review> reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(
            userId, PageRequest.of(page, size));
        List<ReviewResponse> content = reviews.getContent().stream()
            .map(ReviewResponse::from).toList();
        return PageResponse.from(content, reviews);
    }

    // ================================================================
    // 7. Admin: tất cả review
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getAllReviews(Long productId, int page, int size) {
        Page<Review> reviews = (productId != null)
            ? reviewRepository.findByProductId(productId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            : reviewRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

        List<ReviewResponse> content = reviews.getContent().stream()
            .map(ReviewResponse::from).toList();
        return PageResponse.from(content, reviews);
    }

    // ================================================================
    // 8. Admin: Thống kê đánh giá theo sản phẩm
    // ================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<com.fashionshop.backend.module.review.dto.response.ProductReviewStatResponse> getProductReviewStats(Integer categoryId, int page, int size) {
        Page<Object[]> statsPage;
        if (categoryId != null) {
            List<Integer> categoryIds = getCategoryIdsIncludingChildren(categoryId);
            statsPage = reviewRepository.getProductReviewStatsByCategory(categoryIds, PageRequest.of(page, size));
        } else {
            statsPage = reviewRepository.getProductReviewStats(PageRequest.of(page, size));
        }

        List<com.fashionshop.backend.module.review.dto.response.ProductReviewStatResponse> content = statsPage.getContent().stream()
            .map(row -> {
                Long productId = ((Number) row[0]).longValue();
                String productName = (String) row[1];
                Double avgRating = row[2] != null ? Math.round(((Number) row[2]).doubleValue() * 10.0) / 10.0 : 0.0;
                Long totalReviews = ((Number) row[3]).longValue();

                // Lấy ảnh cover từ ProductImage (isPrimary=true, color=null)
                String imageUrl = productImageRepository.findPrimaryByProductId(productId)
                    .map(com.fashionshop.backend.domain.ProductImage::getImageUrl)
                    .orElse(null);

                return com.fashionshop.backend.module.review.dto.response.ProductReviewStatResponse.builder()
                    .productId(productId)
                    .productName(productName)
                    .productImage(imageUrl)
                    .avgRating(avgRating)
                    .totalReviews(totalReviews)
                    .build();
            }).toList();

        return PageResponse.from(content, statsPage);
    }

    // ================================================================
    // Private helpers
    // ================================================================

    /** Lấy ID category + tất cả con (cách đơn giản cho cây 2 cấp). */
    private java.util.List<Integer> getCategoryIdsIncludingChildren(Integer categoryId) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        ids.add(categoryId);
        java.util.List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(categoryId);
        children.forEach(child -> ids.add(child.getId()));
        return ids;
    }

    private Review findAndValidateOwnership(Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.REVIEW_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!review.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        }
        return review;
    }
}
