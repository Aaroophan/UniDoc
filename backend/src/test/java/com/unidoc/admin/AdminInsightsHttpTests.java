package com.unidoc.admin;

import com.unidoc.auth.JwtTokenService;
import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.request.*;
import com.unidoc.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-insights-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue17-insights-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class AdminInsightsHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;
    @Autowired DocumentRequestRepository requests;
    @Autowired RequestStatusHistoryRepository histories;

    @Test
    void roleMatrixPreventsAdminFromUsingStaffCommandsAndOtherRolesFromMonitoring() throws Exception {
        User admin = user(UserRole.ADMIN);
        User staff = user(UserRole.STAFF);
        User student = student();
        DocumentRequest request = create(student, type(admin), RequestStatus.SUBMITTED, DeliveryMethod.ELECTRONIC);
        String[] adminPaths = {
                "/api/admin/requests", "/api/admin/requests/" + request.getId(),
                "/api/admin/dashboard", "/api/admin/reports/requests", "/api/admin/reports/requests.csv"
        };
        for (String path : adminPaths) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(staff)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/staff/requests/{id}", request.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/staff/requests/{id}/start-review", request.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)).contentType("application/json")
                .content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/requests/{id}/attachments/1", request.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/requests/{id}/issued-document", request.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/requests/{id}/issued-document", request.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/requests/{id}/approve", request.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().is4xxClientError());
    }

    @Test
    void monitoringResponseHasNoActionsCredentialsOrFilePaths() throws Exception {
        User admin = user(UserRole.ADMIN);
        User owner = student();
        DocumentRequest request = create(owner, type(admin), RequestStatus.SUBMITTED, DeliveryMethod.PHYSICAL);
        String token = bearer(admin);
        mvc.perform(get("/api/admin/requests").header(HttpHeaders.AUTHORIZATION, token)
                        .param("q", request.getRequestNumber()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].requestNumber").value(request.getRequestNumber()));
        String body = mvc.perform(get("/api/admin/requests/{id}", request.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.availableActions").isEmpty())
                .andExpect(jsonPath("$.history[0].newStatus").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash", "authVersion", "relativePath", "storedFilename", "/api/staff/");
        mvc.perform(get("/api/admin/requests/99999999").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void dashboardFiltersPagingCsvHeadersAndAllRowsMatch() throws Exception {
        User admin = user(UserRole.ADMIN);
        User owner = student();
        DocumentType type = type(admin);
        for (int i = 0; i < 7; i++) create(owner, type, RequestStatus.COMPLETED, DeliveryMethod.PHYSICAL);
        create(owner, type, RequestStatus.REJECTED, DeliveryMethod.ELECTRONIC);
        String token = bearer(admin);
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRequests").value(8))
                .andExpect(jsonPath("$.completedRequests").value(7))
                .andExpect(jsonPath("$.rejectedRequests").value(1))
                .andExpect(jsonPath("$.byDeliveryMethod.PHYSICAL").value(7))
                .andExpect(jsonPath("$.recentRequests.length()").value(8));
        String report = mvc.perform(get("/api/admin/reports/requests").header(HttpHeaders.AUTHORIZATION, token)
                        .param("deliveryMethod", "PHYSICAL").param("status", "COMPLETED")
                        .param("size", "3").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(7))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(report).doesNotContain("REJECTED", "passwordHash");
        var csvResponse = mvc.perform(get("/api/admin/reports/requests.csv")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("deliveryMethod", "PHYSICAL").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("unidoc-request-report-")))
                .andReturn().getResponse();
        assertThat(csvResponse.getContentType()).startsWith("text/csv").contains("UTF-8");
        String csv = new String(csvResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith(SafeCsvWriter.HEADER + "\r\n");
        assertThat(csv.split("UDR-", -1)).hasSize(8);
        assertThat(csv).doesNotContain("REJECTED");
        mvc.perform(get("/api/admin/reports/requests.csv").header(HttpHeaders.AUTHORIZATION, token)
                        .param("status", "READY"))
                .andExpect(status().isOk())
                .andExpect(content().string(SafeCsvWriter.HEADER + "\r\n"));
    }

    @Test
    void allInvalidFiltersAreRejectedWithStableErrors() throws Exception {
        User admin = user(UserRole.ADMIN);
        String token = bearer(admin);
        for (String path : new String[]{"/api/admin/reports/requests", "/api/admin/reports/requests.csv"}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token)
                            .param("submittedFrom", "2026-10-10").param("submittedTo", "2026-10-01"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token).param("status", "UNKNOWN"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token).param("sort", "passwordHash"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
        mvc.perform(get("/api/admin/reports/requests").header(HttpHeaders.AUTHORIZATION, token).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(get("/api/admin/requests").header(HttpHeaders.AUTHORIZATION, token).param("sort", "passwordHash"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("admin-http-" + UUID.randomUUID() + "@example.test", "hash", "Admin HTTP", role));
    }
    private User student() {
        User user = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(user, "ADH-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 2));
        return user;
    }
    private DocumentType type(User admin) {
        String code = "ADH" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        return types.saveAndFlush(new DocumentType(code, "Report " + code,
                "Reporting HTTP test document.", "Bring university ID", false, true, true,
                "Counter", "Bring ID", admin));
    }
    private DocumentRequest create(User owner, DocumentType type, RequestStatus status, DeliveryMethod method) {
        var request = requests.saveAndFlush(new DocumentRequest(owner, type, method,
                "Academic records needed for reporting", LocalDate.now().plusDays(4)));
        request.assignRequestNumber();
        request.updateStatus(status);
        request = requests.saveAndFlush(request);
        histories.saveAndFlush(new RequestStatusHistory(request, null, status, owner, "Submitted for administrator report."));
        return request;
    }
    private String bearer(User user) { return "Bearer " + jwt.issue(user).value(); }
}
