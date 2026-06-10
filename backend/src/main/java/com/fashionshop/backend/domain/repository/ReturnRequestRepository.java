package com.fashionshop.backend.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.ReturnRequest;

import jakarta.persistence.LockModeType;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    boolean existsByOrderId(Long orderId);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<ReturnStatus> statuses);

    Optional<ReturnRequest> findByIdAndUserId(Long id, Long userId);

    Page<ReturnRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "user", "items"})
    Page<ReturnRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"order", "user", "items"})
    Page<ReturnRequest> findByStatusOrderByCreatedAtDesc(ReturnStatus status, Pageable pageable);

    Optional<ReturnRequest> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReturnRequest r WHERE r.id = :id")
    Optional<ReturnRequest> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(ReturnStatus status);

    long countByStatusIn(Collection<ReturnStatus> statuses);

    long countByStatusAndUpdatedAtAfter(ReturnStatus status, LocalDateTime startDate);

    long countByStatusAndCreatedAtBefore(ReturnStatus status, LocalDateTime before);

    long countByStatusAndCreatedAtAfter(ReturnStatus status, LocalDateTime after);

    long countByStatusAndUpdatedAtBetween(ReturnStatus status, LocalDateTime start, LocalDateTime end);

    List<ReturnRequest> findTop5ByStatusOrderByCreatedAtAsc(ReturnStatus status);

    List<ReturnRequest> findTop5ByStatusInOrderByCreatedAtAsc(Collection<ReturnStatus> statuses);

    @Query("SELECT r.status, COUNT(r) FROM ReturnRequest r GROUP BY r.status")
    List<Object[]> getStatusDistribution();

    @Query(value = """
        SELECT
            CASE
                WHEN reason LIKE '[ĐỔI HÀNG]%' THEN 'Đổi hàng'
                WHEN reason LIKE '[KHIẾU NẠI]%' THEN 'Khiếu nại'
                ELSE 'Trả hàng'
            END AS request_type,
            COUNT(*)
        FROM returns
        GROUP BY request_type
        """, nativeQuery = true)
    List<Object[]> getTypeDistribution();

    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM ReturnRequest r " +
        "WHERE r.status IN (com.fashionshop.backend.common.enums.ReturnStatus.COMPLETED, com.fashionshop.backend.common.enums.ReturnStatus.REFUNDED) AND r.updatedAt >= :startDate")
    BigDecimal sumCompletedRefundAmountSince(@Param("startDate") LocalDateTime startDate);
}
