package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItemId(Long orderItemId);

    /** Batch check canReview — lấy danh sách orderItemId đã có review. */
    @Query("SELECT r.orderItem.id FROM Review r WHERE r.orderItem.id IN :ids")
    List<Long> findReviewedOrderItemIds(@Param("ids") Collection<Long> orderItemIds);

    /** Batch fetch reviews by orderItemIds. */
    List<Review> findByOrderItemIdIn(Collection<Long> orderItemIds);

    /** Public: review sản phẩm (tất cả). */
    Page<Review> findByProductId(Long productId, Pageable pageable);

    /** Public: review sản phẩm (filter rating). */
    Page<Review> findByProductIdAndRating(Long productId, Integer rating, Pageable pageable);

    /** Stats: AVG + COUNT. */
    @Query("SELECT AVG(r.rating), COUNT(r) FROM Review r WHERE r.product.id = :pid")
    List<Object[]> getStatsByProductId(@Param("pid") Long productId);

    /** Stats: breakdown 1-5 sao. */
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.product.id = :pid GROUP BY r.rating")
    List<Object[]> getBreakdownByProductId(@Param("pid") Long productId);

    /** Batch stats cho listing: productId, avg, count. */
    @Query("SELECT r.product.id, AVG(r.rating), COUNT(r) FROM Review r WHERE r.product.id IN :pids GROUP BY r.product.id")
    List<Object[]> getBatchStatsByProductIds(@Param("pids") Collection<Long> productIds);

    /** Customer: review của tôi. */
    Page<Review> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Admin: tất cả review. */
    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Admin: Thống kê đánh giá theo sản phẩm */
    @Query(value = "SELECT r.product.id, r.product.name, AVG(r.rating), COUNT(r) FROM Review r GROUP BY r.product.id, r.product.name ORDER BY AVG(r.rating) DESC",
           countQuery = "SELECT COUNT(DISTINCT r.product.id) FROM Review r")
    Page<Object[]> getProductReviewStats(Pageable pageable);

    /** Admin: Thống kê đánh giá theo sản phẩm (lọc theo danh mục) */
    @Query(value = "SELECT r.product.id, r.product.name, AVG(r.rating), COUNT(r) FROM Review r WHERE r.product.category.id IN (:categoryIds) GROUP BY r.product.id, r.product.name ORDER BY AVG(r.rating) DESC",
           countQuery = "SELECT COUNT(DISTINCT r.product.id) FROM Review r WHERE r.product.category.id IN (:categoryIds)")
    Page<Object[]> getProductReviewStatsByCategory(@Param("categoryIds") java.util.List<Integer> categoryIds, Pageable pageable);
}
