package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Customer — danh sách đơn của mình
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, OrderStatus status, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN o.items i " +
           "WHERE o.user.id = :userId " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:keyword IS NULL OR CAST(o.id AS string) LIKE CONCAT('%', :keyword, '%') " +
           "    OR LOWER(i.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findCustomerOrders(@Param("userId") Long userId,
                                   @Param("status") OrderStatus status,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    // Staff — danh sách tất cả đơn
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN o.items i " +
           "WHERE (:status IS NULL OR o.status = :status) " +
           "AND (:categoryId IS NULL OR EXISTS (" +
           "    SELECT 1 FROM OrderItem oi " +
           "    JOIN oi.variant pv " +
           "    JOIN pv.product p " +
           "    WHERE oi.order = o AND (p.category.id = :categoryId OR p.category.parent.id = :categoryId)" +
           ")) " +
           "AND (:keyword IS NULL OR " +
           "    CAST(o.id AS string) LIKE CONCAT('%', :keyword, '%') " +
           "    OR LOWER(i.productName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "    OR LOWER(o.user.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> searchOrders(@Param("status") OrderStatus status,
                             @Param("keyword") String keyword,
                             @Param("categoryId") Long categoryId,
                             Pageable pageable);

    long countByStatus(OrderStatus status);

    long countByStatusAndPackingConfirmed(OrderStatus status, boolean packingConfirmed);

    // Scheduler
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoff);

    List<Order> findByStatusAndDeliveredAtBefore(OrderStatus status, LocalDateTime cutoff);

    // Duplicate order guard
    Optional<Order> findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
        Long userId, LocalDateTime after);

    // Dashboard Stats
    @Query(value = """
        SELECT
            COALESCE((
                SELECT SUM(o.total_amount)
                FROM orders o
                WHERE o.payment_status IN ('PAID', 'REFUNDED')
                  AND o.status IN ('DELIVERED', 'COMPLETED', 'RETURNED')
            ), 0)
            -
            COALESCE((
                SELECT SUM(r.refund_amount)
                FROM returns r
                WHERE r.status = 'COMPLETED'
            ), 0)
        """, nativeQuery = true)
    java.math.BigDecimal getTotalRevenue();

    @Query(value = """
        SELECT d.date, COALESCE(SUM(d.gross), 0) - COALESCE(SUM(d.refund), 0) AS revenue
        FROM (
            SELECT DATE(o.created_at) AS date, SUM(o.total_amount) AS gross, 0 AS refund
            FROM orders o
            WHERE o.created_at >= :startDate
              AND o.payment_status IN ('PAID', 'REFUNDED')
              AND o.status IN ('DELIVERED', 'COMPLETED', 'RETURNED')
            GROUP BY DATE(o.created_at)
            UNION ALL
            SELECT DATE(r.updated_at) AS date, 0 AS gross, SUM(COALESCE(r.refund_amount, 0)) AS refund
            FROM returns r
            WHERE r.updated_at >= :startDate
              AND r.status = 'COMPLETED'
            GROUP BY DATE(r.updated_at)
        ) d
        GROUP BY d.date
        ORDER BY d.date ASC
        """, nativeQuery = true)
    List<Object[]> getRevenueTrend(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> getOrderStatusDistribution();

    @Query("SELECT COALESCE(SUM(o.shippingFee), 0) FROM Order o " +
           "WHERE o.createdAt >= :startDate AND o.status IN :statuses")
    java.math.BigDecimal sumShippingFeeByCreatedAtAfterAndStatusIn(
        @Param("startDate") LocalDateTime startDate,
        @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT COALESCE(AVG(o.shippingFee), 0) FROM Order o " +
           "WHERE o.createdAt >= :startDate AND o.status IN :statuses")
    java.math.BigDecimal avgShippingFeeByCreatedAtAfterAndStatusIn(
        @Param("startDate") LocalDateTime startDate,
        @Param("statuses") List<OrderStatus> statuses);
}
