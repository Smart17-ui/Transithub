package com.transithub.identity.internal;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * SS-16 (backlog #18): once a day, warns the owners of unused accounts and closes the
 * ones that were warned long enough ago. Off in tests, which call the service directly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "transithub.accounts.cleanup-enabled", havingValue = "true", matchIfMissing = true)
class UnusedAccountCleanup {

    private static final Logger log = LoggerFactory.getLogger(UnusedAccountCleanup.class);

    private final AccountClosureService closures;
    private final Clock clock;

    UnusedAccountCleanup(AccountClosureService closures, Clock clock) {
        this.closures = closures;
        this.clock = clock;
    }

    @Scheduled(cron = "${transithub.accounts.cleanup-cron:0 30 2 * * *}", zone = "Africa/Lusaka")
    void run() {
        int warned = closures.warnUnused(clock.instant());
        int closed = closures.closeUnused(clock.instant());
        if (warned + closed > 0) {
            log.info("Unused accounts: {} warned, {} closed", warned, closed);
        }
    }
}
