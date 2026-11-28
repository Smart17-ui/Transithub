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

    /**
     * Every read and change of an address's timestamps happens inside the map's
     * per-key lock ({@code compute}), so concurrent requests can neither crash the
     * clean-up nor lose each other's timestamps.
     */
    boolean tryAcquire(String address) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        if (recent.size() > MAX_TRACKED_ADDRESSES) {
            for (String key : recent.keySet()) {
                recent.computeIfPresent(key, (k, times) ->
                        times.isEmpty() || times.peekLast().isBefore(cutoff) ? null : times);
            }
        }
        boolean[] allowed = {false};
        recent.compute(address, (key, existing) -> {
            Deque<Instant> times = existing == null ? new ArrayDeque<>() : existing;
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.pollFirst();
            }
            if (times.size() < maxPerWindow) {
                times.addLast(now);
                allowed[0] = true;
            }
            return times;
        });
        return allowed[0];
    }
}
