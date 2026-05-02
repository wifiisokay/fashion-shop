package com.fashionshop.backend.domain.repository;

import com.fashionshop.backend.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    /** IPN lookup — tìm payment theo mã giao dịch hệ thống gửi VNPay. */
    Optional<Payment> findByVnpayTxnRef(String vnpayTxnRef);

    /** Customer: xem payment của đơn mình. */
    Optional<Payment> findByOrderIdAndOrder_UserId(Long orderId, Long userId);

    /** Admin: danh sách tất cả giao dịch. */
    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
