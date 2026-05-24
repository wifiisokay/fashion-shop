package com.fashionshop.backend.scheduled;

import com.fashionshop.backend.domain.repository.OutfitSuggestionCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled job: xóa outfit suggestion cache cũ hơn 48h.
 * Chạy lúc 2h sáng hàng ngày.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatScheduler {

    private final OutfitSuggestionCacheRepository cacheRepository;

    @Scheduled(cron = "0 0 2 * * *") // 02:00 hàng ngày
    @Transactional
    public void cleanExpiredOutfitCache() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(48);
        int deleted = cacheRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("ChatScheduler: cleaned {} expired outfit cache entries", deleted);
        }
    }
}
