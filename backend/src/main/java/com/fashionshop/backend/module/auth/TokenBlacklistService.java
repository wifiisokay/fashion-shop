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
 * NOTE: Token sẽ mất khi restart server — đây là giới hạn đã biết của in-memory store.
 * TODO: Migrate sang Redis hoặc DB khi cần persistence.
 *
 * isBlacklisted() tự cleanup token expired (eager cleanup) để không trả nhầm `true`
 * cho token đã hết hạn mà chưa được cleanup định kỳ xử lý.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    // token → expiry time
    private final Map<String, Date> blacklist = new ConcurrentHashMap<>();

    /**
     * Thêm token vào blacklist khi user logout.
     *
     * @param token  raw JWT token
     * @param expiry thời điểm token hết hạn (lấy từ claims) — dùng để cleanup sau
     */
    public void blacklist(String token, Date expiry) {
        blacklist.put(token, expiry);
        log.debug("Token blacklisted, expires at: {}", expiry);
    }

    /**
     * Kiểm tra token có bị blacklist không.
     *
     * <p>Thực hiện eager cleanup: nếu token tìm thấy trong blacklist nhưng đã expired,
     * xóa nó luôn và trả về {@code false} (token invalid anyway, không cần block).
     * Tránh race condition với scheduled cleanup chạy mỗi giờ.
     */
    public boolean isBlacklisted(String token) {
        Date expiry = blacklist.get(token);
        if (expiry == null) {
            return false;
        }
        if (expiry.before(new Date())) {
            // Eager cleanup — token đã expired, xóa khỏi map
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    /**
     * Cleanup token đã hết hạn — chạy mỗi giờ.
     * isBlacklisted() đã eager-cleanup từng token nên đây chỉ là safety net.
     */
    @Scheduled(fixedRate = 3_600_000)
    public void cleanup() {
        Date now = new Date();
        int sizeBefore = blacklist.size();
        blacklist.entrySet().removeIf(e -> e.getValue().before(now));
        int removed = sizeBefore - blacklist.size();
        if (removed > 0) {
            log.debug("Blacklist cleanup: removed {} expired tokens", removed);
        }
    }
}
