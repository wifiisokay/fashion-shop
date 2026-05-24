package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    /** Kiểm tra có return đang active (PENDING hoặc APPROVED) cho order này không. */
    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<ReturnStatus> statuses);

    /** Customer: xem return của order cụ thể. */
    Optional<ReturnRequest> findByIdAndUserId(Long id, Long userId);

    /** Customer: danh sách return của mình. */
    Page<ReturnRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Staff/Admin: tất cả return. */
    Page<ReturnRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Staff/Admin: filter theo status. */
    Page<ReturnRequest> findByStatusOrderByCreatedAtDesc(ReturnStatus status, Pageable pageable);

    /** Tìm return active cho order (dùng để hiển thị trên OrderDetail). */
    Optional<ReturnRequest> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);

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
           "WHERE r.status = 'COMPLETED' AND r.updatedAt >= :startDate")
    BigDecimal sumCompletedRefundAmountSince(@Param("startDate") LocalDateTime startDate);
}
