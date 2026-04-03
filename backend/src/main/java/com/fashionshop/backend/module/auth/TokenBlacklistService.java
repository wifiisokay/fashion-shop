package com.fashionshop.backend.module.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token blacklist cho logout.
 * Cleanup tự động mỗi giờ để tránh memory leak.
 *
 * NOTE: Token sẽ mất khi restart server.
 * Nếu cần persistence → dùng Redis hoặc bảng DB blacklisted_tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    // token → expiry time
    private final Map<String, Date> blacklist = new ConcurrentHashMap<>();

    /**
     * Thêm token vào blacklist khi user logout.
     */
    public void blacklist(String token, Date expiry) {
        blacklist.put(token, expiry);
        log.debug("Token blacklisted, expires at: {}", expiry);
    }

    /**
     * Kiểm tra token có bị blacklist không.
     */
    public boolean isBlacklisted(String token) {
        return blacklist.containsKey(token);
    }

    /**
     * Cleanup token đã hết hạn — chạy mỗi giờ.
     */
    @Scheduled(fixedRate = 3_600_000)
    public void cleanup() {
        Date now = new Date();
        int sizeBefore = blacklist.size();
        blacklist.entrySet().removeIf(e -> e.getValue().before(now));
        log.debug("Blacklist cleanup: removed {} expired tokens", sizeBefore - blacklist.size());
    }
}
