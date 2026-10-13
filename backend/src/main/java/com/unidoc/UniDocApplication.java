package com.unidoc;

import com.unidoc.config.DemoProperties;
import com.unidoc.demo.DemoResetSafety;
import com.unidoc.demo.DemoResetService;
import com.unidoc.demo.DemoSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UniDocApplication {
    private static final Logger log = LoggerFactory.getLogger(UniDocApplication.class);

    public static void main(String[] args) {
        if (!Boolean.parseBoolean(System.getenv("UNIDOC_RESET_DEMO"))) {
            SpringApplication.run(UniDocApplication.class, args);
            return;
        }

        // Reset is always a separate, non-web process. Refuse missing/mistyped
        // confirmation before Spring starts or connects to MySQL.
        try {
            DemoResetSafety.requireGuards(new DemoProperties(false, true,
                    System.getenv("UNIDOC_RESET_CONFIRMATION")), System.getenv("SERVER_PORT"));
        } catch (IllegalStateException refused) {
            System.err.println(refused.getMessage());
            System.exit(2);
            return;
        }
        SpringApplication application = new SpringApplication(UniDocApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            context.getBean(DemoResetService.class).resetDatabase();
            if (context.getBean(DemoProperties.class).seedEnabled()) {
                DemoSeedService.SeedSummary summary = context.getBean(DemoSeedService.class).seed();
                log.info("Demo reset reseeding completed newAccounts={} newProfiles={} "
                                + "newDocumentTypes={} newRequests={} newHistories={} "
                                + "newNotifications={} newFiles={}",
                        summary.accounts(), summary.profiles(), summary.types(),
                        summary.requests(), summary.histories(), summary.notifications(), summary.files());
            }
            log.info("Demo reset run finished successfully; no web server started.");
        } catch (Exception failure) {
            log.error("Demo reset failed; inspect the cause and database state before retrying.", failure);
            System.exit(1);
        }
    }
}
