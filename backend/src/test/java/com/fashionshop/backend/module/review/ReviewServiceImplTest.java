package com.fashionshop.backend.module.review;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.domain.*;
import com.fashionshop.backend.domain.repository.OrderItemRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ReturnItemRepository;
import com.fashionshop.backend.domain.repository.ReviewRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.module.review.dto.request.CreateReviewRequest;
import com.fashionshop.backend.module.review.dto.request.UpdateReviewRequest;
import com.fashionshop.backend.module.review.dto.response.ReviewResponse;
import com.fashionshop.backend.module.review.dto.response.ReviewStatsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock ReviewRepository reviewRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ProductRepository productRepository;
    @Mock ReturnItemRepository returnItemRepository;

    @InjectMocks ReviewServiceImpl sut;

    // ==================== Helpers ====================

    private User mockUser(Long id) {
        return User.builder().id(id).fullName("Nguyễn Văn A").email("a@test.com").password("x").build();
    }

    private Product mockProduct() {
        return Product.builder().id(100L).name("Áo thun Basic").basePrice(BigDecimal.valueOf(200000)).build();
    }

    private Order mockOrder(OrderStatus status, Long userId) {
        return Order.builder()
            .id(1L).user(mockUser(userId)).status(status)
            .paymentStatus(status == OrderStatus.COMPLETED ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID)
            .items(new java.util.ArrayList<>())
            .build();
    }

    private OrderItem mockOrderItem(Order order) {
        return OrderItem.builder()
            .id(10L).order(order).productId(100L)
            .productName("Áo thun Basic").colorName("Đen").size("M")
            .unitPrice(BigDecimal.valueOf(200000)).quantity(1)
            .subtotal(BigDecimal.valueOf(200000))
            .build();
    }

    private Review mockReview(Long userId) {
        User user = mockUser(userId);
        Order order = mockOrder(OrderStatus.COMPLETED, userId);
        OrderItem item = mockOrderItem(order);
        return Review.builder()
            .id(1L).user(user).orderItem(item).product(mockProduct())
            .rating(5).comment("Tuyệt vời").createdAt(LocalDateTime.now())
            .build();
    }

    private CreateReviewRequest createRequest(Long orderItemId, int rating, String comment) {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setOrderItemId(orderItemId);
        req.setRating(rating);
        req.setComment(comment);
        return req;
    }

    // ==================== createReview ====================

    @Nested
    @DisplayName("createReview")
    class CreateReview {

        @Test
        @DisplayName("Tạo review thành công cho đơn COMPLETED")
        void success_delivered() {
            Order order = mockOrder(OrderStatus.COMPLETED, 1L);
            OrderItem item = mockOrderItem(order);
            Product product = mockProduct();

            when(orderItemRepository.findById(10L)).thenReturn(Optional.of(item));
            when(returnItemRepository.sumQuantityByOrderItemAndStatuses(eq(10L), anyCollection())).thenReturn(0L);
            when(reviewRepository.existsByOrderItemId(10L)).thenReturn(false);
            when(productRepository.findById(100L)).thenReturn(Optional.of(product));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                r.setId(1L);
                r.setCreatedAt(LocalDateTime.now());
                return r;
            });

            ReviewResponse response = sut.createReview(1L, createRequest(10L, 5, "Rất tốt"));

            assertNotNull(response);
            assertEquals(5, response.getRating());
            assertEquals("Rất tốt", response.getComment());
            verify(reviewRepository).save(any(Review.class));
        }

        @Test
        @DisplayName("Tạo review thành công cho đơn COMPLETED")
        void success_completed() {
            Order order = mockOrder(OrderStatus.COMPLETED, 1L);
            OrderItem item = mockOrderItem(order);

            when(orderItemRepository.findById(10L)).thenReturn(Optional.of(item));
            when(returnItemRepository.sumQuantityByOrderItemAndStatuses(eq(10L), anyCollection())).thenReturn(0L);
            when(reviewRepository.existsByOrderItemId(10L)).thenReturn(false);
            when(productRepository.findById(100L)).thenReturn(Optional.of(mockProduct()));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                r.setId(2L);
                r.setCreatedAt(LocalDateTime.now());
                return r;
            });

            ReviewResponse response = sut.createReview(1L, createRequest(10L, 4, null));

            assertNotNull(response);
            assertEquals(4, response.getRating());
        }

        @Test
        @DisplayName("Lỗi — OrderItem không tồn tại")
        void fail_orderItemNotFound() {
            when(orderItemRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createReview(1L, createRequest(99L, 5, "ok")));
            assertEquals("ORDER_014", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Lỗi — không phải chủ đơn hàng")
        void fail_notOwner() {
            Order order = mockOrder(OrderStatus.COMPLETED, 2L); // user 2 là chủ
            OrderItem item = mockOrderItem(order);
            when(orderItemRepository.findById(10L)).thenReturn(Optional.of(item));

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createReview(1L, createRequest(10L, 5, "ok"))); // user 1 gọi
            assertEquals("AUTH_005", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Lỗi — đơn chưa giao (PENDING)")
        void fail_notDelivered() {
            Order order = mockOrder(OrderStatus.PENDING, 1L);
            OrderItem item = mockOrderItem(order);
            when(orderItemRepository.findById(10L)).thenReturn(Optional.of(item));

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createReview(1L, createRequest(10L, 5, "ok")));
            assertEquals("REVIEW_003", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Lỗi — đã review rồi (duplicate)")
        void fail_alreadyReviewed() {
            Order order = mockOrder(OrderStatus.COMPLETED, 1L);
            OrderItem item = mockOrderItem(order);
            when(orderItemRepository.findById(10L)).thenReturn(Optional.of(item));
            when(returnItemRepository.sumQuantityByOrderItemAndStatuses(eq(10L), anyCollection())).thenReturn(0L);
            when(reviewRepository.existsByOrderItemId(10L)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.createReview(1L, createRequest(10L, 5, "ok")));
            assertEquals("REVIEW_002", ex.getErrorCode().getCode());
        }
    }

    // ==================== updateReview ====================

    @Nested
    @DisplayName("updateReview")
    class UpdateReview {

        @Test
        @DisplayName("Sửa review thành công trong 7 ngày")
        void success() {
            Review review = mockReview(1L);
            review.setCreatedAt(LocalDateTime.now().minusDays(3)); // 3 ngày trước → editable

            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
            when(reviewRepository.save(any(Review.class))).thenReturn(review);

            UpdateReviewRequest req = new UpdateReviewRequest();
            req.setRating(3);
            req.setComment("Sửa lại");

            ReviewResponse response = sut.updateReview(1L, 1L, req);

            assertEquals(3, response.getRating());
            assertEquals("Sửa lại", response.getComment());
            verify(reviewRepository).save(review);
        }

        @Test
        @DisplayName("Lỗi — quá 7 ngày không cho sửa")
        void fail_expired() {
            Review review = mockReview(1L);
            review.setCreatedAt(LocalDateTime.now().minusDays(10)); // 10 ngày → expired

            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

            UpdateReviewRequest req = new UpdateReviewRequest();
            req.setRating(2);

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.updateReview(1L, 1L, req));
            assertEquals("REVIEW_006", ex.getErrorCode().getCode());
        }

        @Test
        @DisplayName("Lỗi — sửa review người khác")
        void fail_notOwner() {
            Review review = mockReview(2L); // user 2 sở hữu
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

            UpdateReviewRequest req = new UpdateReviewRequest();
            req.setRating(1);

            BusinessException ex = assertThrows(BusinessException.class,
                () -> sut.updateReview(1L, 1L, req)); // user 1 gọi
            assertEquals("AUTH_005", ex.getErrorCode().getCode());
        }
    }

    // ==================== deleteReview ====================

    @Nested
    @DisplayName("deleteReview")
    class DeleteReview {

        @Test
        @DisplayName("Xóa review thành công trong 7 ngày")
        void success() {
            Review review = mockReview(1L);
            review.setCreatedAt(LocalDateTime.now().minusDays(1));

            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
            doNothing().when(reviewRepository).delete(review);

            sut.deleteReview(1L, 1L);

            verify(reviewRepository).delete(review);
        }

        @Test
        @DisplayName("Lỗi — quá 7 ngày")
        void fail_expired() {
            Review review = mockReview(1L);
            review.setCreatedAt(LocalDateTime.now().minusDays(8));

            when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

            assertThrows(BusinessException.class, () -> sut.deleteReview(1L, 1L));
        }

        @Test
        @DisplayName("Lỗi — review không tồn tại")
        void fail_notFound() {
            when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> sut.deleteReview(1L, 99L));
        }
    }

    // ==================== getProductReviews ====================

    @Nested
    @DisplayName("getProductReviews")
    class GetProductReviews {

        @Test
        @DisplayName("Lấy danh sách review sản phẩm — không filter rating")
        void success_noFilter() {
            Review review = mockReview(1L);
            when(reviewRepository.findByProductId(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));

            PageResponse<ReviewResponse> result = sut.getProductReviews(100L, null, 0, 10, "newest");

            assertEquals(1, result.getContent().size());
            assertEquals(5, result.getContent().get(0).getRating());
        }

        @Test
        @DisplayName("Lấy danh sách review sản phẩm — filter 5 sao")
        void success_filterRating() {
            Review review = mockReview(1L);
            when(reviewRepository.findByProductIdAndRating(eq(100L), eq(5), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));

            PageResponse<ReviewResponse> result = sut.getProductReviews(100L, 5, 0, 10, "highest");

            assertEquals(1, result.getContent().size());
        }
    }

    // ==================== getReviewStats ====================

    @Nested
    @DisplayName("getReviewStats")
    class GetReviewStats {

        @Test
        @DisplayName("Thống kê rating — có review")
        void success_withReviews() {
            when(reviewRepository.getStatsByProductId(100L))
                .thenReturn(java.util.Collections.<Object[]>singletonList(new Object[]{4.5, 10L}));
            when(reviewRepository.getBreakdownByProductId(100L))
                .thenReturn(List.of(
                    new Object[]{5, 6L},
                    new Object[]{4, 3L},
                    new Object[]{3, 1L}
                ));

            ReviewStatsResponse stats = sut.getReviewStats(100L);

            assertEquals(4.5, stats.getAvgRating());
            assertEquals(10, stats.getTotalReviews());
            assertEquals(6, stats.getBreakdown().get(5));
            assertEquals(0, stats.getBreakdown().get(2)); // no 2-star reviews
            assertEquals(0, stats.getBreakdown().get(1)); // no 1-star reviews
        }

        @Test
        @DisplayName("Thống kê rating — chưa có review")
        void success_noReviews() {
            when(reviewRepository.getStatsByProductId(100L))
                .thenReturn(java.util.Collections.<Object[]>singletonList(new Object[]{null, 0L}));
            when(reviewRepository.getBreakdownByProductId(100L))
                .thenReturn(List.of());

            ReviewStatsResponse stats = sut.getReviewStats(100L);

            assertNull(stats.getAvgRating());
            assertEquals(0, stats.getTotalReviews());
        }
    }

    // ==================== getMyReviews ====================

    @Nested
    @DisplayName("getMyReviews")
    class GetMyReviews {

        @Test
        @DisplayName("Danh sách review của tôi — trang đầu")
        void success() {
            Review review = mockReview(1L);
            when(reviewRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));

            PageResponse<ReviewResponse> result = sut.getMyReviews(1L, 0, 10);

            assertEquals(1, result.getContent().size());
            assertEquals("Nguyễn Văn A", result.getContent().get(0).getCustomerName());
        }
    }
}
