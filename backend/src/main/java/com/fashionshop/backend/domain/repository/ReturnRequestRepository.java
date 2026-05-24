package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.common.enums.ReturnStatus;
import com.fashionshop.backend.domain.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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
}
