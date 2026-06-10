package dev.sakus.geyseredu.authservice;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, long windowMillis) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowMillis = Math.max(1000, windowMillis);
    }

    public boolean allow(String key) {
        long now = Instant.now().toEpochMilli();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAtMillis >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(current.startedAtMillis, current.count + 1);
        });
        return window.count <= maxAttempts;
    }

    private record Window(long startedAtMillis, int count) {
    }
}
