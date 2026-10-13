package com.unidoc.demo;

import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.admin.AdminDashboardService;
import com.unidoc.admin.RequestReportFilter;
import com.unidoc.admin.RequestReportService;
import com.unidoc.admin.RequestReportRow;
import com.unidoc.notification.NotificationRepository;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.request.DeliveryMethod;
import com.unidoc.request.DocumentRequestRepository;
import com.unidoc.request.IssuedDocumentRepository;
import com.unidoc.request.RequestStatus;
import com.unidoc.request.RequestStatusHistoryRepository;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-issue19-seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_SEED_DEMO=false", "UNIDOC_RESET_DEMO=false",
        "UNIDOC_STORAGE_ROOT=target/issue19-seed-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DemoSeedServiceTests {
    @Autowired DemoSeedService seeder;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;
    @Autowired DocumentRequestRepository requests;
    @Autowired RequestStatusHistoryRepository history;
    @Autowired NotificationRepository notifications;
    @Autowired IssuedDocumentRepository issued;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DemoResetService reset;
    @Autowired AdminDashboardService dashboard;
    @Autowired RequestReportService report;

    @Test
    void bothDemoTransactionalServicesAreProxiedBySpring() {
        // Regression: @Transactional services must remain subclassable by Spring CGLIB.
        assertThat(AopUtils.isAopProxy(seeder)).isTrue();
        assertThat(AopUtils.isAopProxy(reset)).isTrue();
    }

    @Test
    void seedIsIdempotentWithoutOverwritingManualWorkflowAndCredentials() throws Exception {
        DemoSeedService.SeedSummary created = seeder.seed();
        assertThat(created).isEqualTo(new DemoSeedService.SeedSummary(4, 2, 5, 10, 38, 39, 7));
        assertThat(requests.count()).isEqualTo(10);
        assertThat(types.count()).isEqualTo(5);
        assertThat(profiles.count()).isEqualTo(2);
        assertThat(users.count()).isEqualTo(4);
        assertThat(history.count()).isEqualTo(38);
        assertThat(notifications.count()).isEqualTo(39);
        // Final-release cross-feature gate: the real dashboard and report queries must
        // agree on the known deterministic seed, without private test-only counters.
        long adminId = users.findByNormalizedEmail(DemoSeedFixtures.ADMIN_EMAIL).orElseThrow().getId();
        var seedDashboard = dashboard.dashboard(adminId);
        assertThat(seedDashboard.totalRequests()).isEqualTo(10);
        assertThat(seedDashboard.pendingRequests()).isEqualTo(3);
        assertThat(seedDashboard.approvedRequests()).isEqualTo(1);
        assertThat(seedDashboard.processingRequests()).isEqualTo(1);
        assertThat(seedDashboard.rejectedRequests()).isEqualTo(1);
        assertThat(seedDashboard.readyRequests()).isEqualTo(2);
        assertThat(seedDashboard.completedRequests()).isEqualTo(2);
        assertThat(seedDashboard.byDeliveryMethod()).containsEntry(DeliveryMethod.ELECTRONIC, 6L)
                .containsEntry(DeliveryMethod.PHYSICAL, 4L);
        assertThat(seedDashboard.byDocumentType()).extracting(group -> group.count())
                .containsExactlyInAnyOrder(2L, 2L, 2L, 2L, 2L);
        assertThat(seedDashboard.recentRequests()).hasSize(10);
        var allRows = RequestReportFilter.parse(null, null, null, null, null);
        assertThat(report.report(adminId, allRows, 0, 3, null, null).totalItems()).isEqualTo(10);
        String seedCsv = new String(report.csv(adminId, allRows, null, null), StandardCharsets.UTF_8);
        assertThat(seedCsv.lines().filter(line -> line.startsWith("UDR-DEMO-")).count()).isEqualTo(10);
        var readyElectronic = RequestReportFilter.parse(null, null, "READY", null, "ELECTRONIC");
        assertThat(report.report(adminId, readyElectronic, 0, 20, null, null).items())
                .extracting(RequestReportRow::requestNumber).containsExactly("UDR-DEMO-0006");

        for (var type : DemoSeedFixtures.TYPES) {
            var catalogue = types.findByCode(type.code()).orElseThrow();
            assertThat(catalogue.getName()).isEqualTo(type.name());
            assertThat(catalogue.isElectronicAvailable()).isTrue();
            assertThat(catalogue.isPhysicalAvailable()).isTrue();
        }
        for (var sample : DemoSeedFixtures.REQUESTS) {
            var record = requests.findByRequestNumber(sample.number()).orElseThrow();
            assertThat(record.getStatus()).isEqualTo(sample.status());
            assertThat(record.getDeliveryMethod()).isEqualTo(sample.method());
            assertThat(record.getDocumentTypeNameSnapshot()).isNotBlank();
            if (sample.method() == DeliveryMethod.PHYSICAL) {
                assertThat(record.getCollectionLocationSnapshot()).isNotBlank();
                assertThat(issued.existsByRequestId(record.getId())).isFalse();
            }
        }
        for (String email : new String[] { DemoSeedFixtures.ADMIN_EMAIL, DemoSeedFixtures.STAFF_EMAIL,
                DemoSeedFixtures.STUDENT_EMAIL, DemoSeedFixtures.SECOND_STUDENT_EMAIL }) {
            var account = users.findByNormalizedEmail(email).orElseThrow();
            assertThat(account.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(passwordEncoder.matches(DemoSeedFixtures.LOCAL_DEMO_PASSWORD,
                    account.getPasswordHash())).isTrue();
        }
        assertThat(users.findByNormalizedEmail(DemoSeedFixtures.ADMIN_EMAIL).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);
        var ready = requests.findByRequestNumber("UDR-DEMO-0006").orElseThrow();
        var completed = requests.findByRequestNumber("UDR-DEMO-0009").orElseThrow();
        assertThat(issued.findByRequestId(ready.getId()).orElseThrow().getDownloadCount()).isZero();
        assertThat(issued.findByRequestId(completed.getId()).orElseThrow().getDownloadCount()).isEqualTo(1);
        assertThat(completed.getCompletedAt()).isNotNull();
        var collected = requests.findByRequestNumber("UDR-DEMO-0010").orElseThrow();
        assertThat(collected.getCollectionDate()).isNotNull();
        assertThat(collected.getCollectionNote()).isNotBlank();

        var metadata = issued.findByRequestId(ready.getId()).orElseThrow();
        Path samplePdf = Path.of("target/issue19-seed-test-uploads").toAbsolutePath()
                .resolve(metadata.getRelativePath());
        assertThat(Files.exists(samplePdf)).isTrue();
        assertThat(Files.readAllBytes(samplePdf)).startsWith("%PDF-".getBytes());
        assertThat(metadata.getSizeBytes()).isEqualTo(Files.size(samplePdf));

        var admin = users.findByNormalizedEmail(DemoSeedFixtures.ADMIN_EMAIL).orElseThrow();
        admin.resetPasswordHash(passwordEncoder.encode("ChangedDemoPassword#7"));
        users.saveAndFlush(admin);
        var manuallyEditedType = types.findByCode("STUDENT_STATUS_LETTER").orElseThrow();
        manuallyEditedType.replaceEditable(manuallyEditedType.getCode(), manuallyEditedType.getName(),
                "Updated only by the administrator during a demo.", manuallyEditedType.getRequirements(),
                manuallyEditedType.isSupportingDocumentsRequired(), true, true,
                manuallyEditedType.getCollectionLocation(), manuallyEditedType.getCollectionInstructions(),
                manuallyEditedType.isActive(), admin);
        types.saveAndFlush(manuallyEditedType);
        var submitted = requests.findByRequestNumber("UDR-DEMO-0001").orElseThrow();
        long beforeUpdateVersion = submitted.getVersion();
        submitted.updateStatus(RequestStatus.UNDER_REVIEW);
        requests.saveAndFlush(submitted);
        // Repository saveAndFlush may merge a detached entity and return another instance.
        // Re-query the persisted @Version instead of reading the stale original reference.
        long version = requests.findByRequestNumber("UDR-DEMO-0001").orElseThrow().getVersion();
        assertThat(version).isGreaterThan(beforeUpdateVersion);
        var repeated = seeder.seed();
        assertThat(repeated).isEqualTo(new DemoSeedService.SeedSummary(0, 0, 0, 0, 0, 0, 0));
        assertThat(requests.findByRequestNumber("UDR-DEMO-0001").orElseThrow().getStatus())
                .isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(requests.findByRequestNumber("UDR-DEMO-0001").orElseThrow().getVersion())
                .isEqualTo(version);
        assertThat(types.findByCode("STUDENT_STATUS_LETTER").orElseThrow().getDescription())
                .isEqualTo("Updated only by the administrator during a demo.");
        assertThat(passwordEncoder.matches("ChangedDemoPassword#7",
                users.findByNormalizedEmail(DemoSeedFixtures.ADMIN_EMAIL).orElseThrow().getPasswordHash())).isTrue();
        Path marker = samplePdf.getParent().resolve("preserve-marker.txt");
        Files.writeString(marker, "keep");
        assertThatThrownBy(reset::resetDatabase).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reset refused");
        assertThat(Files.readString(marker)).isEqualTo("keep");
        assertThat(requests.count()).isEqualTo(10);
    }
}
