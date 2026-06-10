package dev.sakus.geyseredu.authservice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {
    @Test
    void deniesRequestsAfterLimitWithinWindow() {
        RateLimiter limiter = new RateLimiter(2, 1000);

        assertTrue(limiter.allow("player-a"));
        assertTrue(limiter.allow("player-a"));
        assertFalse(limiter.allow("player-a"));
        assertTrue(limiter.allow("player-b"));
    }

    @Test
    void resetsAfterWindowExpires() throws Exception {
        RateLimiter limiter = new RateLimiter(1, 1000);

        assertTrue(limiter.allow("player-a"));
        assertFalse(limiter.allow("player-a"));
        Thread.sleep(1050);
        assertTrue(limiter.allow("player-a"));
    }
}
