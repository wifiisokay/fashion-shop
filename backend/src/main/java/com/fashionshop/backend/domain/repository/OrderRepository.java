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

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    // Staff — danh sách tất cả đơn
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = :status " +
           "AND (CAST(o.id AS string) LIKE %:keyword% OR o.note LIKE %:keyword%) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> searchByStatusAndKeyword(@Param("status") OrderStatus status,
                                         @Param("keyword") String keyword,
                                         Pageable pageable);

    // Scheduler
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoff);

    List<Order> findByStatusAndDeliveredAtBefore(OrderStatus status, LocalDateTime cutoff);

    // Duplicate order guard
    Optional<Order> findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
        Long userId, LocalDateTime after);
}
