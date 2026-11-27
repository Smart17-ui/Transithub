package com.transithub.shared.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's single source of "now". Services take a {@link Clock}
 * instead of calling {@code Instant.now()}, so tests can fix the time.
 * Stored timestamps are UTC; local (Africa/Lusaka) time is applied only for
 * display and for calendar rules such as reference years (R-18).
 */
@Configuration
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
