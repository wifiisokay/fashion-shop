package com.fashionshop.backend.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public boolean isAllowed(String key, int maxRequests, long windowMillis) {
        long now = System.currentTimeMillis();
        Counter counter = counters.computeIfAbsent(key, k -> new Counter(now, 0));

        synchronized (counter) {
            if (now - counter.windowStartMillis >= windowMillis) {
                counter.windowStartMillis = now;
                counter.count = 0;
            }

            if (counter.count >= maxRequests) {
                return false;
            }

            counter.count++;
            counter.lastAccessMillis = now;
            return true;
        }
    }

    @Scheduled(fixedRate = 600_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(e -> now - e.getValue().lastAccessMillis > 3_600_000);
    }

    private static class Counter {
        private long windowStartMillis;
        private long lastAccessMillis;
        private int count;

        private Counter(long now, int count) {
            this.windowStartMillis = now;
            this.lastAccessMillis = now;
            this.count = count;
        }
    }
}
