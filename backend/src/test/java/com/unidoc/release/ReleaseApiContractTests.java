package com.unidoc.release;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final scope gate: all /api routes must belong to the accepted API design.
 * This deliberately locks down the HTTP surface without browser automation or a second runtime.
 * OPTIONS/preflight is handled by Spring Security CORS and is not a controller mapping.
 */
@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-issue20-route-contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_SEED_DEMO=false", "UNIDOC_RESET_DEMO=false",
        "UNIDOC_STORAGE_ROOT=target/issue20-api-contract-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ReleaseApiContractTests {

    private static final Set<String> APPROVED = Set.of(
            "GET /api/health",
            "POST /api/auth/register", "POST /api/auth/login", "GET /api/auth/me",
            "GET /api/student/profile", "PUT /api/student/profile",
            "GET /api/document-types", "GET /api/document-types/{id}",
            "POST /api/student/requests", "GET /api/student/requests",
            "GET /api/student/requests/{requestId}",
            "POST /api/student/requests/{requestId}/information-response",
            "GET /api/student/requests/{requestId}/attachments/{attachmentId}",
            "GET /api/student/requests/{requestId}/issued-document",
            "GET /api/staff/requests", "GET /api/staff/requests/{requestId}",
            "POST /api/staff/requests/{requestId}/start-review",
            "POST /api/staff/requests/{requestId}/request-information",
            "POST /api/staff/requests/{requestId}/approve",
            "POST /api/staff/requests/{requestId}/reject",
            "POST /api/staff/requests/{requestId}/start-processing",
            "POST /api/staff/requests/{requestId}/issued-document",
            "POST /api/staff/requests/{requestId}/ready-for-collection",
            "POST /api/staff/requests/{requestId}/complete-collection",
            "GET /api/staff/requests/{requestId}/attachments/{attachmentId}",
            "GET /api/staff/requests/{requestId}/issued-document",
            "GET /api/notifications", "GET /api/notifications/unread-count",
            "POST /api/notifications/{notificationId}/read", "POST /api/notifications/read-all",
            "GET /api/admin/users", "POST /api/admin/users",
            "PATCH /api/admin/users/{userId}/status",
            "POST /api/admin/users/{userId}/reset-password",
            "GET /api/admin/document-types", "POST /api/admin/document-types",
            "GET /api/admin/document-types/{id}", "PUT /api/admin/document-types/{id}",
            "PATCH /api/admin/document-types/{id}/status",
            "GET /api/admin/requests", "GET /api/admin/requests/{requestId}",
            "GET /api/admin/dashboard",
            "GET /api/admin/reports/requests", "GET /api/admin/reports/requests.csv"
    );

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Test
    void exposedApiMethodsMatchTheApprovedApiAndExcludeUnplannedMutations() {
        Set<String> actual = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            for (String path : info.getPatternValues()) {
                if (!path.startsWith("/api/")) continue;
                for (var method : info.getMethodsCondition().getMethods()) {
                    actual.add(method.name() + " " + path);
                }
            }
        }

        assertThat(actual).containsExactlyInAnyOrderElementsOf(APPROVED);
        assertThat(actual).noneMatch(route -> route.startsWith("DELETE "));
        assertThat(actual).noneMatch(route -> route.contains("/cancel")
                || route.contains("/payment") || route.contains("/assign")
                || route.contains("/refresh-token") || route.contains("/password-recovery"));
    }
}
