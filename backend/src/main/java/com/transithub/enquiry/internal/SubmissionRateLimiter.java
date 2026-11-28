package com.transithub.enquiry.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits how many Contact-form messages one address may send in a window,
 * so the public form cannot be used to flood the support inbox. Kept in
 * memory: a restart resets it, which is acceptable for this purpose.
 */
@Component
class SubmissionRateLimiter {

    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxPerWindow;
    private final Duration window;

    SubmissionRateLimiter(Clock clock,
                          @Value("${transithub.enquiry.max-per-window:5}") int maxPerWindow,
                          @Value("${transithub.enquiry.window-minutes:10}") int windowMinutes) {
        this.clock = clock;
        this.maxPerWindow = maxPerWindow;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    boolean tryAcquire(String address) {
        Instant now = clock.instant();
        if (recent.size() > MAX_TRACKED_ADDRESSES) {
            recent.values().removeIf(times -> times.isEmpty() || times.peekLast().isBefore(now.minus(window)));
        }
        Deque<Instant> times = recent.computeIfAbsent(address, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(window))) {
                times.pollFirst();
            }
            if (times.size() >= maxPerWindow) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
