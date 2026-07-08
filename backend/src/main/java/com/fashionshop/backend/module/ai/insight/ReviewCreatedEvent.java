package com.fashionshop.backend.module.ai.insight;

/**
 * Spring Application Event được publish khi có review mới được tạo hoặc cập nhật.
 *
 * Cách dùng trong ReviewServiceImpl (thêm 2 dòng, không sửa logic cũ):
 *
 *   // Inject ApplicationEventPublisher
 *   private final ApplicationEventPublisher eventPublisher;
 *
 *   // Sau reviewRepository.save(review):
 *   if (review.getProduct() != null) {
 *       eventPublisher.publishEvent(new ReviewCreatedEvent(review.getProduct().getId()));
 *   }
 *
 * AiInsightCacheEvictListener lắng nghe event này và evict cache Redis tương ứng.
 */
public record ReviewCreatedEvent(Long productId) {}
