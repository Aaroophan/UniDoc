package com.unidoc.request;

import com.unidoc.auth.JwtTokenService;
import com.unidoc.catalogue.DocumentType;
import com.unidoc.catalogue.DocumentTypeRepository;
import com.unidoc.profile.StudentProfile;
import com.unidoc.profile.StudentProfileRepository;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP role boundaries, multipart parts, validation, version conflict and response shapes. */
@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-review-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue14-review-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ReviewWorkflowHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;
    @Autowired DocumentRequestService students;
    @Autowired StaffRequestService staff;

    @Test
    void staffDecisionsEnforceRolePayloadValidationAndOptimisticVersions() throws Exception {
        User staffUser = user(UserRole.STAFF);
        User owner = student();
        User admin = user(UserRole.ADMIN);
        var created = submit(owner);
        var reviewed = staff.startReview(staffUser.getId(), created.id(), created.version());
        String valid = "{\"message\":\"Please provide a supporting document.\",\"expectedVersion\":" + reviewed.version() + "}";
        String path = "/api/staff/requests/{id}/request-information";
        mvc.perform(post(path, created.id()).contentType("application/json").content(valid))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post(path, created.id()).header("Authorization", token(owner))
                        .contentType("application/json").content(valid))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post(path, created.id()).header("Authorization", token(admin))
                        .contentType("application/json").content(valid))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post(path, created.id()).header("Authorization", token(staffUser))
                        .contentType("application/json")
                        .content("{\"message\":\"  no \" ,\"expectedVersion\":" + reviewed.version() + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.message").exists());
        mvc.perform(post("/api/staff/requests/{id}/reject", created.id())
                        .header("Authorization", token(staffUser)).contentType("application/json")
                        .content("{\"reason\":\" \" ,\"expectedVersion\":" + reviewed.version() + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(post(path, created.id()).header("Authorization", token(staffUser))
                        .contentType("application/json").content(valid))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ADDITIONAL_INFORMATION_REQUIRED"))
                .andExpect(jsonPath("$.additionalInformationMessage").value("Please provide a supporting document."));
        mvc.perform(post(path, created.id()).header("Authorization", token(staffUser))
                        .contentType("application/json").content(valid))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_VERSION_CONFLICT"));

        // Separate request: approve then processing, with explicit confirmations and no auto-processing.
        var next = submit(owner);
        var inReview = staff.startReview(staffUser.getId(), next.id(), next.version());
        mvc.perform(post("/api/staff/requests/{id}/approve", next.id())
                        .header("Authorization", token(staffUser)).contentType("application/json")
                        .content("{\"expectedVersion\":" + inReview.version() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.availableActions[0]").value("START_PROCESSING"));
        mvc.perform(post("/api/staff/requests/{id}/start-processing", next.id())
                        .header("Authorization", token(staffUser)).contentType("application/json")
                        .content("{\"expectedVersion\":" + (inReview.version() + 1) + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));

        var rejected = submit(owner);
        var rejectionReview = staff.startReview(staffUser.getId(), rejected.id(), rejected.version());
        mvc.perform(post("/api/staff/requests/{id}/reject", rejected.id())
                        .header("Authorization", token(staffUser)).contentType("application/json")
                        .content("{\"reason\":\"Student documentation is incomplete.\",\"expectedVersion\":" + rejectionReview.version() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Student documentation is incomplete."))
                .andExpect(jsonPath("$.availableActions.length()").value(0));
    }

    @Test
    void informationResponseMultipartValidatesFilesAndEnforcesOwnershipAndStatus() throws Exception {
        User owner = student();
        User stranger = student();
        User reviewer = user(UserRole.STAFF);
        User admin = user(UserRole.ADMIN);
        var submitted = submit(owner);
        var reviewing = staff.startReview(reviewer.getId(), submitted.id(), submitted.version());
        var asked = staff.requestInformation(reviewer.getId(), submitted.id(),
                new RequestInformationRequest("Provide a clear copy of your student identity.", reviewing.version()));
        String endpoint = "/api/student/requests/{id}/information-response";
        MockMultipartFile response = response("I have attached my student ID.", asked.version());
        mvc.perform(multipart(endpoint, submitted.id()).file(response))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(endpoint, submitted.id()).file(response).header("Authorization", token(stranger)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
        mvc.perform(multipart(endpoint, submitted.id()).file(response).header("Authorization", token(reviewer)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(endpoint, submitted.id()).file(response).header("Authorization", token(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(endpoint, submitted.id()).file(response(" ", asked.version()))
                        .header("Authorization", token(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.message").exists());
        mvc.perform(multipart(endpoint, submitted.id()).file(response)
                        .file(new MockMultipartFile("files", "fake.pdf", "application/pdf", "not a PDF".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", token(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        mvc.perform(multipart(endpoint, submitted.id()).file(response)
                        .file(new MockMultipartFile("files", "identity.pdf", "application/pdf",
                                "%PDF-1.7\nHTTP information response".getBytes(StandardCharsets.US_ASCII)))
                        .header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.additionalInformationMessage").value((Object) null))
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].kind").value("ADDITIONAL_INFORMATION"))
                .andExpect(jsonPath("$.history.length()").value(4));
        mvc.perform(multipart(endpoint, submitted.id()).file(response).header("Authorization", token(owner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_VERSION_CONFLICT"));
    }

    private MockMultipartFile response(String message, long version) {
        return new MockMultipartFile("response", "", "application/json",
                ("{\"message\":\"" + message + "\",\"expectedVersion\":" + version + "}")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("review-http-" + UUID.randomUUID() + "@example.test", "hash",
                "Review HTTP User", role));
    }

    private User student() {
        User account = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(account,
                "RVH-" + UUID.randomUUID().toString().substring(0, 8), null,
                "Science", "Computing", "Computer Science", 2));
        return account;
    }

    private StudentRequestDetailResponse submit(User owner) {
        User admin = user(UserRole.ADMIN);
        String code = "RVH" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        DocumentType type = types.saveAndFlush(new DocumentType(code, "Review HTTP " + code,
                "A document for HTTP workflow tests.", "Present a student ID.", false, true, true,
                "Desk A", "Bring ID", admin));
        return students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Required for a university programme", LocalDate.now().plusDays(7), DeliveryMethod.ELECTRONIC),
                List.of());
    }

    private String token(User user) { return "Bearer " + jwt.issue(user).value(); }
}
