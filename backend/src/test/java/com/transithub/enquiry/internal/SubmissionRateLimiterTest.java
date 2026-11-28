package com.transithub.enquiry.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionRateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-27T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void manyAddressesAtOnceNeverFailAndEachStaysWithinItsLimit() throws Exception {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(clock, 3, 10);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.concurrent.atomic.AtomicInteger allowedForShared = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<java.util.concurrent.Future<?>> work = new java.util.ArrayList<>();
        for (int i = 0; i < 24_000; i++) {
            int n = i;
            work.add(pool.submit(() -> {
                limiter.tryAcquire("address-" + n);              // grows past the clean-up threshold
                if (n % 100 == 0 && limiter.tryAcquire("shared")) {
                    allowedForShared.incrementAndGet();
                }
            }));
        }
        for (java.util.concurrent.Future<?> f : work) {
            f.get();                                              // any exception inside fails the test
        }
        pool.shutdown();
        org.junit.jupiter.api.Assertions.assertEquals(3, allowedForShared.get());
    }

    @Test
    void anAddressIsLimitedWithinTheWindowAndFreedAfterIt() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(clock, 2, 10);

        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"), "third message within ten minutes");
        assertTrue(limiter.tryAcquire("10.0.0.2"), "other visitors are unaffected");

        now.set(now.get().plus(Duration.ofMinutes(11)));
        assertTrue(limiter.tryAcquire("10.0.0.1"), "the window has passed");
    }
}
