package com.unidoc.request;

import com.unidoc.auth.JwtTokenService;
import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import com.unidoc.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-staff-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue13-staff-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class StaffRequestHttpTests {
    private static final byte[] PDF = "%PDF-1.7\nrequest review proof".getBytes(StandardCharsets.US_ASCII);
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService jwt;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private StudentProfileRepository profiles;
    @Autowired private DocumentTypeRepository types;
    @Autowired private DocumentRequestService studentRequests;

    @Test
    void twoStaffSeeIdenticalWorkListAndOpenDetailWithoutStartingReview() throws Exception {
        User one = user(UserRole.STAFF);
        User two = user(UserRole.STAFF);
        User student = student();
        var created = submit(student, type(), false);
        for (User staff : List.of(one, two)) {
            mvc.perform(get("/api/staff/requests?q={q}&status=SUBMITTED&sort=createdAt&direction=desc&page=0&size=10",
                            created.requestNumber()).header("Authorization", token(staff)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].requestNumber").value(created.requestNumber()))
                    .andExpect(jsonPath("$.items[0].studentName").value(student.getFullName()))
                    .andExpect(jsonPath("$.items[0].registrationNumber").isNotEmpty())
                    .andExpect(jsonPath("$.items[0].version").value(created.version()));
            mvc.perform(get("/api/staff/requests/{id}", created.id()).header("Authorization", token(staff)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.student.id").value(student.getId()))
                    .andExpect(jsonPath("$.student.email").value(student.getEmail()))
                    .andExpect(jsonPath("$.student.faculty").value("Science"))
                    .andExpect(jsonPath("$.student.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.student.authVersion").doesNotExist())
                    .andExpect(jsonPath("$.availableActions[0]").value("START_REVIEW"))
                    .andExpect(jsonPath("$.history.length()").value(1))
                    .andExpect(jsonPath("$.version").value(created.version()));
        }
    }

    @Test
    void startReviewWritesHistoryUsesVersionAndRejectsSecondStaffStaleView() throws Exception {
        User a = user(UserRole.STAFF);
        User b = user(UserRole.STAFF);
        var created = submit(student(), type(), false);
        String payload = "{\"expectedVersion\":" + created.version() + "}";
        var result = mvc.perform(post("/api/staff/requests/{id}/start-review", created.id())
                        .header("Authorization", token(a)).contentType("application/json").content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.version").value(created.version() + 1))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].newStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.history[1].changedBy.id").value(a.getId()))
                .andExpect(jsonPath("$.availableActions.length()").value(3))
                .andReturn();
        JsonNode details = json.readTree(result.getResponse().getContentAsString());
        long nextVersion = details.get("version").asLong();
        mvc.perform(post("/api/staff/requests/{id}/start-review", created.id())
                        .header("Authorization", token(b)).contentType("application/json").content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_VERSION_CONFLICT"));
        mvc.perform(post("/api/staff/requests/{id}/start-review", created.id())
                        .header("Authorization", token(b)).contentType("application/json")
                        .content("{\"expectedVersion\":" + nextVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_TRANSITION"));
        mvc.perform(get("/api/staff/requests/{id}", created.id()).header("Authorization", token(b)))
                .andExpect(jsonPath("$.history.length()").value(2));
    }

    @Test
    void staffDownloadHasSafeHeadersAndCannotReadMismatchedRequestAttachment() throws Exception {
        User staff = user(UserRole.STAFF);
        User owner = student();
        var created = submit(owner, type(), true);
        var another = submit(owner, type(), true);
        long attachment = created.attachments().getFirst().id();
        mvc.perform(get("/api/staff/requests/{id}/attachments/{file}", created.id(), attachment)
                        .header("Authorization", token(staff)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(get("/api/staff/requests/{id}/attachments/{file}", created.id(),
                        another.attachments().getFirst().id()).header("Authorization", token(staff)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));
        mvc.perform(get("/api/staff/requests/{id}/attachments/{file}", 99999999, attachment)
                        .header("Authorization", token(staff)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void rolesAreIsolatedAndInvalidFilterAndActionPayloadsAreRejected() throws Exception {
        User staff = user(UserRole.STAFF);
        User student = student();
        User admin = user(UserRole.ADMIN);
        var created = submit(student, type(), false);
        for (var route : List.of("/api/staff/requests", "/api/staff/requests/" + created.id(),
                "/api/staff/requests/" + created.id() + "/attachments/11")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            mvc.perform(get(route).header("Authorization", token(student))).andExpect(status().isForbidden());
            mvc.perform(get(route).header("Authorization", token(admin))).andExpect(status().isForbidden());
        }
        for (User wrong : List.of(student, admin)) {
            mvc.perform(post("/api/staff/requests/{id}/start-review", created.id())
                            .header("Authorization", token(wrong))
                            .contentType("application/json").content("{\"expectedVersion\":" + created.version() + "}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/staff/requests/{id}/start-review", created.id())
                        .contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isUnauthorized());
        for (String bad : List.of("{}", "{\"expectedVersion\":null}", "{\"expectedVersion\":-1}")) {
            mvc.perform(post("/api/staff/requests/{id}/start-review", created.id())
                            .header("Authorization", token(staff)).contentType("application/json").content(bad))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String query : List.of("size=101", "page=-1", "sort=student.passwordHash", "direction=sideways",
                "submittedFrom=2026-10-19&submittedTo=2026-10-18", "documentTypeId=0", "status=INVALID",
                "deliveryMethod=INVALID")) {
            mvc.perform(get("/api/staff/requests?" + query).header("Authorization", token(staff)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
        mvc.perform(get("/api/staff/requests/99999999").header("Authorization", token(staff)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
        mvc.perform(put("/api/staff/requests/{id}", created.id()).header("Authorization", token(staff)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/staff/requests/{id}", created.id()).header("Authorization", token(staff)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void inactiveStaffCannotAccessEvenAnAlreadyIssuedJwt() throws Exception {
        User staff = user(UserRole.STAFF);
        String issuedToken = token(staff);
        var created = submit(student(), type(), false);
        staff.changeStatus(UserStatus.INACTIVE);
        users.saveAndFlush(staff);
        mvc.perform(get("/api/staff/requests").header("Authorization", issuedToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        mvc.perform(get("/api/staff/requests/{id}", created.id()).header("Authorization", issuedToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("staff-http-" + UUID.randomUUID() + "@example.test", "hash",
                "Staff HTTP " + UUID.randomUUID().toString().substring(0, 8), role));
    }
    private User student() {
        User account = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(account,
                "REG-" + UUID.randomUUID().toString().substring(0, 8), "0771234567",
                "Science", "Computing", "Computer Science", 2));
        return account;
    }
    private DocumentType type() {
        User admin = user(UserRole.ADMIN);
        String code = "STH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return types.saveAndFlush(new DocumentType(code, "Type " + code, "A university document.",
                "Bring ID.", false, true, true, "Main office", "Bring ID.", admin));
    }
    private StudentRequestDetailResponse submit(User owner, DocumentType type, boolean includePdf) {
        var files = includePdf
                ? List.of(new MockMultipartFile("files", "evidence.pdf", "application/pdf", PDF))
                : List.<MockMultipartFile>of();
        return studentRequests.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Required for student records.", LocalDate.now().plusDays(5), DeliveryMethod.ELECTRONIC),
                new java.util.ArrayList<org.springframework.web.multipart.MultipartFile>(files));
    }
    private String token(User user) { return "Bearer " + jwt.issue(user).value(); }
}
