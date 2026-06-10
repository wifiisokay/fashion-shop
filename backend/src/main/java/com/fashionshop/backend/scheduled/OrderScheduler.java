package com.fashionshop.backend.scheduled;

import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.OrderItem;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.PaymentRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final PaymentRepository paymentRepository;

    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void autoCancelExpiredPayment() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        List<Order> expired = orderRepository
            .findByStatusAndCreatedAtBefore(OrderStatus.AWAITING_PAYMENT, cutoff);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Auto-cancel: found {} expired AWAITING_PAYMENT orders", expired.size());

        for (Order order : expired) {
            var payment = paymentRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
            if (payment == null) {
                log.warn("Skip auto-cancel order #{} because payment is missing", order.getId());
                continue;
            }
            if (payment.getStatus() != PaymentStatus.PENDING) {
                log.info("Skip auto-cancel order #{} because payment is {}", order.getId(), payment.getStatus());
                continue;
            }

            restoreStock(order);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason("Qua han thanh toan (tu dong huy sau 15 phut)");
            orderRepository.save(order);

            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.info("Auto-cancelled order #{}", order.getId());
        }
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            if (item.getVariant() != null) {
                variantRepository.increaseStock(item.getVariant().getId(), item.getQuantity());
            }
        }
    }
}
