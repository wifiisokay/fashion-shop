package com.fashionshop.backend.module.order;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.Payment;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.PaymentRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tự động hủy các đơn hàng VNPay ở trạng thái AWAITING_PAYMENT
 * khi user bỏ dở (không thanh toán trong 20 phút).
 *
 * <p>Lý do cần scheduler này: VNPay chỉ gửi IPN khi user thực sự nhấn
 * thanh toán trên cổng. Nếu user đóng tab → IPN không bao giờ đến →
 * tồn kho bị "treo" vô thời hạn.</p>
 *
 * <p>Chạy mỗi 15 phút, hủy đơn tạo trước 20 phút chưa thanh toán.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireScheduler {

    /** Đơn hàng tồn tại quá EXPIRE_MINUTES phút sẽ bị hủy tự động. */
    private static final int EXPIRE_MINUTES = 20;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductVariantRepository variantRepository;

    /**
     * Chạy mỗi 15 phút (fixedRate = 15 * 60 * 1000 ms).
     * Mỗi đơn được xử lý trong transaction riêng để lỗi 1 đơn
     * không rollback toàn bộ batch.
     */
    @Scheduled(fixedRate = 900_000)
    public void expireStaleVnpayOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(EXPIRE_MINUTES);
        List<Order> staleOrders = orderRepository
                .findByStatusAndCreatedAtBefore(OrderStatus.AWAITING_PAYMENT, cutoff);

        if (staleOrders.isEmpty()) {
            return;
        }

        log.info("[ORDER_EXPIRE] Found {} stale AWAITING_PAYMENT order(s) older than {} min",
                staleOrders.size(), EXPIRE_MINUTES);

        for (Order order : staleOrders) {
            try {
                expireOneOrder(order);
            } catch (Exception ex) {
                log.error("[ORDER_EXPIRE] Failed to expire orderId={}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Xử lý 1 đơn trong transaction riêng biệt (@Transactional ở đây có tác dụng
     * vì được gọi từ một bean khác — không bị Spring AOP bypass như self-invocation).
     */
    @Transactional
    public void expireOneOrder(Order order) {
        // Re-fetch trong transaction để lấy lock hiện tại
        order = orderRepository.findById(order.getId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            // Đã được xử lý bởi IPN hoặc scheduler lần trước
            return;
        }

        // 1. Hoàn lại tồn kho
        order.getItems().forEach(item -> {
            if (item.getVariant() != null) {
                variantRepository.increaseStock(item.getVariant().getId(), item.getQuantity());
            }
        });

        // 2. Hủy đơn
        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        order.setCancelReason("Hủy tự động: hết thời gian thanh toán VNPay (" + EXPIRE_MINUTES + " phút)");
        orderRepository.save(order);

        // 3. Cập nhật payment → FAILED nếu vẫn còn PENDING
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }

        log.info("[ORDER_EXPIRE] Cancelled orderId={} (created={})", order.getId(), order.getCreatedAt());
    }
}
