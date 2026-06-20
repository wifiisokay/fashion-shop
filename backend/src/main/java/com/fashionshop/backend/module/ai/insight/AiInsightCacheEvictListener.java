package com.fashionshop.backend.module.ai.insight;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Lắng nghe ReviewCreatedEvent → evict AI Insight cache của sản phẩm tương ứng.
 *
 * @Async: evict chạy bất đồng bộ, không làm chậm luồng tạo review của user.
 * Nếu evict lỗi: AiInsightCacheManager đã handle internally (best-effort).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiInsightCacheEvictListener {

    private final AiInsightCacheManager cacheManager;

    @Async
    @EventListener
    public void onReviewCreated(ReviewCreatedEvent event) {
        log.debug("[AI_INSIGHT] evict_triggered productId={}", event.productId());
        cacheManager.evict(event.productId());
    }
}
