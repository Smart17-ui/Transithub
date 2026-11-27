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
