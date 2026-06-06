package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findTopByOrderIdAndStatusOrderByCreatedAtDesc(Long orderId, PaymentStatus status);

    Optional<Payment> findByVnpayTxnRef(String vnpayTxnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p JOIN FETCH p.order WHERE p.vnpayTxnRef = :txnRef")
    Optional<Payment> findByVnpayTxnRefForUpdate(@Param("txnRef") String txnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p JOIN FETCH p.order WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    Optional<Payment> findByOrderIdAndOrder_UserId(Long orderId, Long userId);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
