package com.unidoc.demo;

import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.config.DemoProperties;
import com.unidoc.request.DocumentRequestRepository;
import com.unidoc.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for Issue 19: other integration tests require empty H2 databases.
 * This class deliberately does not override UNIDOC_SEED_DEMO in @SpringBootTest.
 * Surefire must isolate all test contexts even when the host Windows environment
 * contains UNIDOC_SEED_DEMO=true for manual local demonstrations.
 */
@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-issue19-auto-isolation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue19-auto-isolation-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DemoAutomaticStartupIsolationTests {
    @Autowired DemoProperties demoProperties;
    @Autowired UserRepository users;
    @Autowired DocumentTypeRepository documentTypes;
    @Autowired DocumentRequestRepository requests;

    @Test
    void mavenTestsAreNotSeededOrResetWhenDeveloperDemoFlagsAreEnabled() {
        assertThat(demoProperties.seedEnabled()).isFalse();
        assertThat(demoProperties.resetEnabled()).isFalse();
        assertThat(users.count()).isZero();
        assertThat(documentTypes.count()).isZero();
        assertThat(requests.count()).isZero();
    }
}
