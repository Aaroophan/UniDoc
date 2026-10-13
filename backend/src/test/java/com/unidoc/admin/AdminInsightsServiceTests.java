package com.unidoc.admin;

import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.request.*;
import com.unidoc.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-insights-service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue17-insights-service-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class AdminInsightsServiceTests {
    @Autowired AdminRequestService monitoring;
    @Autowired AdminDashboardService dashboard;
    @Autowired RequestReportService report;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;
    @Autowired DocumentRequestRepository requests;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired AdminAccess access;

    @Test
    void emptyDatabaseReturnsZeroCountsAndHeaderOnlyCsv() {
        User admin = user(UserRole.ADMIN);
        var stats = dashboard.dashboard(admin.getId());
        assertThat(stats.totalRequests()).isZero();
        assertThat(stats.pendingRequests()).isZero();
        assertThat(stats.approvedRequests()).isZero();
        assertThat(stats.processingRequests()).isZero();
        assertThat(stats.rejectedRequests()).isZero();
        assertThat(stats.readyRequests()).isZero();
        assertThat(stats.completedRequests()).isZero();
        assertThat(stats.byDeliveryMethod()).containsEntry(DeliveryMethod.ELECTRONIC, 0L)
                .containsEntry(DeliveryMethod.PHYSICAL, 0L);
        assertThat(stats.byDocumentType()).isEmpty();
        assertThat(stats.recentRequests()).isEmpty();
        var filters = RequestReportFilter.parse(null, null, null, null, null);
        assertThat(report.report(admin.getId(), filters, null, null, null, null).totalItems()).isZero();
        assertThat(new String(report.csv(admin.getId(), filters, null, null), StandardCharsets.UTF_8))
                .isEqualTo(SafeCsvWriter.HEADER + "\r\n");
    }

    @Test
    void dashboardDefinitionsGroupByRequestSnapshotAndShowTenNewest() {
        User admin = user(UserRole.ADMIN);
        User owner = student();
        DocumentType type = documentType(admin);
        for (RequestStatus status : RequestStatus.values()) {
            create(owner, type, status, status == RequestStatus.READY ? DeliveryMethod.PHYSICAL : DeliveryMethod.ELECTRONIC,
                    "Purpose for " + status);
        }
        create(owner, type, RequestStatus.SUBMITTED, DeliveryMethod.PHYSICAL, "Purpose again");
        create(owner, type, RequestStatus.PROCESSING, DeliveryMethod.ELECTRONIC, "Processing again");
        create(owner, type, RequestStatus.SUBMITTED, DeliveryMethod.ELECTRONIC, "Another request");
        // Historical reporting uses the name as at submission, not the current catalogue name.
        type.replaceEditable(type.getCode(), "Renamed Document", type.getDescription(), type.getRequirements(),
                false, true, true, "Counter", "Bring ID", true, admin);
        types.saveAndFlush(type);
        create(owner, type, RequestStatus.COMPLETED, DeliveryMethod.PHYSICAL, "After rename");

        var result = dashboard.dashboard(admin.getId());
        assertThat(result.totalRequests()).isEqualTo(12);
        assertThat(result.pendingRequests()).isEqualTo(5); // 3 SUBMITTED, 1 UNDER_REVIEW, 1 ADDITIONAL_INFORMATION_REQUIRED
        assertThat(result.approvedRequests()).isEqualTo(1);
        assertThat(result.processingRequests()).isEqualTo(2);
        assertThat(result.rejectedRequests()).isEqualTo(1);
        assertThat(result.readyRequests()).isEqualTo(1);
        assertThat(result.completedRequests()).isEqualTo(2);
        assertThat(result.byDeliveryMethod()).containsEntry(DeliveryMethod.ELECTRONIC, 9L)
                .containsEntry(DeliveryMethod.PHYSICAL, 3L);
        assertThat(result.byDocumentType()).hasSize(2);
        assertThat(result.byDocumentType()).extracting(g -> g.documentTypeName())
                .containsExactlyInAnyOrder(typeName(type), "Renamed Document");
        assertThat(result.byDocumentType()).extracting(g -> g.count()).containsExactlyInAnyOrder(11L, 1L);
        assertThat(result.recentRequests()).hasSize(10);
        assertThat(result.recentRequests().getFirst().documentTypeName()).isEqualTo("Renamed Document");
    }

    @Test
    void paginatedMonitoringAndReportShareFiltersButOnlyReportCsvReturnsAllMatching() {
        User admin = user(UserRole.ADMIN);
        User owner = student();
        DocumentType type = documentType(admin);
        String distinctive = "Unique CSV purpose \"quoted\", comma and café";
        for (int i = 0; i < 9; i++) {
            create(owner, type, i == 0 ? RequestStatus.COMPLETED : RequestStatus.READY,
                    DeliveryMethod.PHYSICAL, i == 0 ? distinctive : "Report purpose " + i);
        }
        create(owner, type, RequestStatus.REJECTED, DeliveryMethod.ELECTRONIC, "Excluded");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var filters = RequestReportFilter.parse(today.minusDays(1), today, null, type.getId(), "PHYSICAL");
        var first = report.report(admin.getId(), filters, 0, 3, "createdAt", "asc");
        assertThat(first.totalItems()).isEqualTo(9);
        assertThat(first.items()).hasSize(3);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(report.report(admin.getId(), filters, 2, 3, "createdAt", "asc").items()).hasSize(3);
        String csv = new String(report.csv(admin.getId(), filters, "createdAt", "asc"), StandardCharsets.UTF_8);
        assertThat(csv.split("UDR-", -1)).hasSize(10); // 9 data rows, no pagination
        assertThat(csv).contains("café").contains("\"Unique CSV purpose \"\"quoted\"\", comma and café\"");
        assertThat(csv).doesNotContain("Excluded");
        var monitored = monitoring.list(admin.getId(), null, "READY", type.getId(), "PHYSICAL",
                today.minusDays(1), today, null, 0, 3, "createdAt", "desc");
        assertThat(monitored.totalItems()).isEqualTo(8);
        assertThat(monitored.items()).allSatisfy(row -> {
            assertThat(row.registrationNumber()).isEqualTo(profiles.findByUserId(owner.getId()).orElseThrow().getRegistrationNumber());
            assertThat(row.status()).isEqualTo(RequestStatus.READY);
        });
        assertThat(monitoring.detail(admin.getId(), monitored.items().getFirst().id()).availableActions()).isEmpty();
        assertThat(monitoring.detail(admin.getId(), monitored.items().getFirst().id()).history()).hasSize(1);
    }

    @Test
    void invalidParametersAndWrongRolesFailInServiceLayer() {
        User admin = user(UserRole.ADMIN);
        User staff = user(UserRole.STAFF);
        User student = student();
        assertThatThrownBy(() -> dashboard.dashboard(staff.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> report.report(student.getId(), RequestReportFilter.parse(null, null, null, null, null),
                0, 20, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> monitoring.list(staff.getId(), null, null, null, null,
                null, null, null, null, null, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> monitoring.detail(staff.getId(), 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> RequestReportFilter.parse(LocalDate.now(), LocalDate.now().minusDays(1),
                null, null, null)).isInstanceOf(com.unidoc.common.exception.InvalidQueryParameterException.class);
        assertThatThrownBy(() -> RequestReportFilter.parse(null, null, null, -1L, null))
                .isInstanceOf(com.unidoc.common.exception.InvalidQueryParameterException.class);
        assertThatThrownBy(() -> report.report(admin.getId(), RequestReportFilter.parse(null, null, null, null, null),
                0, 1, "passwordHash", "asc"))
                .isInstanceOf(com.unidoc.common.exception.InvalidQueryParameterException.class);
        assertThatThrownBy(() -> report.csv(admin.getId(), RequestReportFilter.parse(null, null, null, null, null),
                "invalid", "desc")).isInstanceOf(com.unidoc.common.exception.InvalidQueryParameterException.class);
    }

    private static String typeName(DocumentType type) { return "Academic " + type.getCode(); }
    private User user(UserRole role) {
        return users.saveAndFlush(new User("insight-" + UUID.randomUUID() + "@example.test", "hash", "Insights Tester", role));
    }
    private User student() {
        User user = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(user, "INS-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 2));
        return user;
    }
    private DocumentType documentType(User admin) {
        String code = "INS" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        return types.saveAndFlush(new DocumentType(code, "Academic " + code,
                "Academic request report document.", "Bring university ID", false, true, true,
                "Counter", "Bring ID", admin));
    }
    private DocumentRequest create(User owner, DocumentType type, RequestStatus status, DeliveryMethod method, String purpose) {
        DocumentRequest request = requests.saveAndFlush(new DocumentRequest(owner, type, method, purpose,
                LocalDate.now().plusDays(4)));
        request.assignRequestNumber();
        request.updateStatus(status);
        request = requests.saveAndFlush(request);
        history.saveAndFlush(new RequestStatusHistory(request, null, status, owner, "Seeded state for reporting."));
        return request;
    }
}
