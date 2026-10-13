package com.unidoc.demo;

import com.unidoc.config.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** A normal app startup seeds only when explicitly enabled; reset mode seeds after deletion. */
@Component
public final class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private final DemoProperties properties;
    private final DemoSeedService service;

    public DemoDataSeeder(DemoProperties properties, DemoSeedService service) {
        this.properties = properties;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.seedEnabled() && !properties.resetEnabled()) {
            DemoSeedService.SeedSummary result = service.seed();
            log.info("Demo seed completed newAccounts={} newProfiles={} newDocumentTypes={} "
                            + "newRequests={} newHistories={} newNotifications={} newFiles={}",
                    result.accounts(), result.profiles(), result.types(), result.requests(),
                    result.histories(), result.notifications(), result.files());
        }
    }
}
