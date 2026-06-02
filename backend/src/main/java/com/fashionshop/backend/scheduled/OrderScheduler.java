package com.fashionshop.backend.scheduled;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.OrderItem;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled jobs cho Order:
 * 1. Auto-cancel: AWAITING_PAYMENT quá 15 phút → CANCELLED + hoàn stock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;

    /**
     * Mỗi 5 phút: tìm đơn AWAITING_PAYMENT quá 15 phút → auto-cancel.
     */
    @Scheduled(fixedRate = 300_000) // 5 phút
    @Transactional
    public void autoCancelExpiredPayment() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        List<Order> expired = orderRepository
            .findByStatusAndCreatedAtBefore(OrderStatus.AWAITING_PAYMENT, cutoff);

        if (expired.isEmpty()) return;

        log.info("Auto-cancel: found {} expired AWAITING_PAYMENT orders", expired.size());

        for (Order order : expired) {
            restoreStock(order);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason("Quá hạn thanh toán (tự động hủy sau 15 phút)");
            orderRepository.save(order);
            log.info("Auto-cancelled order #{}", order.getId());
        }
    }

    /** Hoàn stock cho tất cả items trong đơn hàng. */
    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getVariant() != null) {
                ProductVariant variant = item.getVariant();
                variant.setStockQuantity(variant.getStockQuantity() + item.getQuantity());
                variantRepository.save(variant);
            }
        }
    }
}
