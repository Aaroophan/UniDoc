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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-electronic-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue15-electronic-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ElectronicDocumentHttpTests {
    private static final byte[] PDF = "%PDF-1.7\nHTTP electronic delivery".getBytes(StandardCharsets.US_ASCII);
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired DocumentRequestService students;
    @Autowired StaffRequestService staff;
    @Autowired ElectronicDocumentService electronic;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;

    @Test
    void uploadAndDownloadsHaveCorrectMethodPartsRoleSecurityAndHeaders() throws Exception {
        User reviewer = user(UserRole.STAFF);
        User owner = student();
        User stranger = student();
        User admin = user(UserRole.ADMIN);
        var processing = processing(owner, reviewer);
        String endpoint = "/api/staff/requests/" + processing.id() + "/issued-document";
        for (User wrong : List.of(owner, admin)) {
            mvc.perform(multipart(endpoint).file(pdf()).param("expectedVersion", String.valueOf(processing.version()))
                    .header("Authorization", token(wrong))).andExpect(status().isForbidden());
        }
        mvc.perform(multipart(endpoint).file(pdf()).param("expectedVersion", String.valueOf(processing.version())))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(endpoint).file(pdf()).header("Authorization", token(reviewer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(multipart(endpoint).param("expectedVersion", String.valueOf(processing.version()))
                .header("Authorization", token(reviewer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_EMPTY"));
        mvc.perform(multipart(endpoint).file(pdf()).file(pdf())
                .param("expectedVersion", String.valueOf(processing.version()))
                .header("Authorization", token(reviewer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(multipart(endpoint).file(new MockMultipartFile("file", "not.pdf", "text/plain", PDF))
                .param("expectedVersion", String.valueOf(processing.version())).header("Authorization", token(reviewer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        mvc.perform(multipart(endpoint).file(pdf()).param("expectedVersion", "-1")
                .header("Authorization", token(reviewer)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(multipart(endpoint).file(pdf()).param("expectedVersion", String.valueOf(processing.version()))
                .header("Authorization", token(reviewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.issuedDocument.downloadCount").value(0))
                .andExpect(jsonPath("$.history[4].newStatus").value("READY"));
        var ready = staff.getDetail(reviewer.getId(), processing.id());
        mvc.perform(multipart(endpoint).file(pdf()).param("expectedVersion", String.valueOf(ready.version()))
                .header("Authorization", token(reviewer)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ISSUED_DOCUMENT_ALREADY_EXISTS"));
        mvc.perform(multipart(endpoint).file(pdf()).param("expectedVersion", String.valueOf(processing.version()))
                .header("Authorization", token(reviewer)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_VERSION_CONFLICT"));
        for (User wrong : List.of(owner, admin)) {
            mvc.perform(get(endpoint).header("Authorization", token(wrong))).andExpect(status().isForbidden());
        }
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(get(endpoint).header("Authorization", token(reviewer)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        String studentEndpoint = "/api/student/requests/" + ready.id() + "/issued-document";
        mvc.perform(get(studentEndpoint)).andExpect(status().isUnauthorized());
        mvc.perform(get(studentEndpoint).header("Authorization", token(stranger)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
        mvc.perform(get(studentEndpoint).header("Authorization", token(reviewer)))
                .andExpect(status().isForbidden());
        mvc.perform(get(studentEndpoint).header("Authorization", token(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(get(studentEndpoint).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(get(studentEndpoint).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
        mvc.perform(get("/api/student/requests/{id}", ready.id()).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.issuedDocument.downloadCount").value(2))
                .andExpect(jsonPath("$.issuedDocument.firstDownloadedAt").isNotEmpty());
    }

    @Test
    void attemptedDownloadBeforeReadyHasDocumentNotReadyError() throws Exception {
        User reviewer = user(UserRole.STAFF);
        User owner = student();
        var processing = processing(owner, reviewer);
        mvc.perform(get("/api/student/requests/{id}/issued-document", processing.id())
                .header("Authorization", token(owner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENT_NOT_READY"));
        mvc.perform(get("/api/staff/requests/{id}/issued-document", processing.id())
                .header("Authorization", token(reviewer)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENT_NOT_READY"));
    }
    private MockMultipartFile pdf() { return new MockMultipartFile("file", "issued document.pdf", "application/pdf", PDF); }
    private String token(User user) { return "Bearer " + jwt.issue(user).value(); }
    private User user(UserRole role) {
        return users.saveAndFlush(new User("elc-http-" + UUID.randomUUID() + "@example.test", "hash", "HTTP Tester", role));
    }
    private User student() {
        User owner = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(owner, "ELCH-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 3));
        return owner;
    }
    private StaffRequestDetailResponse processing(User owner, User reviewer) {
        User admin = user(UserRole.ADMIN);
        String code = "ELCH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        DocumentType type = types.saveAndFlush(new DocumentType(code, "Electronic HTTP " + code,
                "HTTP test document type.", "Provide student ID", false, true, true,
                "Student office", "Bring ID", admin));
        var created = students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Required for a scholarship application", LocalDate.now().plusDays(5),
                DeliveryMethod.ELECTRONIC), List.of());
        var reviewed = staff.startReview(reviewer.getId(), created.id(), created.version());
        var approved = staff.approve(reviewer.getId(), created.id(), reviewed.version());
        return staff.startProcessing(reviewer.getId(), created.id(), approved.version());
    }
}
