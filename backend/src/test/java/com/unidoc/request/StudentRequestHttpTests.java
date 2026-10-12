package com.unidoc.request;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-request-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue11-request-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class StudentRequestHttpTests {

    private static final byte[] PDF = "%PDF-1.7\nmultpart test file".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;
    @Autowired ObjectMapper mapper;

    @Test
    void validMultipartSubmissionReturns201JsonAndStudentCanListDetailAndDownload() throws Exception {
        User a = student();
        User b = student();
        DocumentType type = documentType(true, true, true);
        String requestJson = payload(type.getId(), "Required for scholarship consideration.",
                LocalDate.now().plusDays(4), "ELECTRONIC");
        var result = mvc.perform(multipart("/api/student/requests")
                        .file(jsonPart(requestJson)).file(pdfPart("../identification.pdf"))
                        .header("Authorization", token(a)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.requestNumber").value(org.hamcrest.Matchers.startsWith("UDR-")))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].originalFilename").value("identification.pdf"))
                .andExpect(jsonPath("$.attachments[0].relativePath").doesNotExist())
                .andExpect(jsonPath("$.attachments[0].storedFilename").doesNotExist())
                .andExpect(jsonPath("$.student.passwordHash").doesNotExist())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        long requestId = body.get("id").asLong();
        long attachmentId = body.get("attachments").get(0).get("id").asLong();

        mvc.perform(get("/api/student/requests?status=SUBMITTED&deliveryMethod=ELECTRONIC&page=0&size=10")
                        .header("Authorization", token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(requestId));
        mvc.perform(get("/api/student/requests/{id}", requestId).header("Authorization", token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId))
                .andExpect(jsonPath("$.availableActions.length()").value(0));
        mvc.perform(get("/api/student/requests/{id}/attachments/{file}", requestId, attachmentId)
                        .header("Authorization", token(a)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(get("/api/student/requests/{id}", requestId).header("Authorization", token(b)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
        mvc.perform(get("/api/student/requests/{id}/attachments/{file}", requestId, attachmentId)
                        .header("Authorization", token(b)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
        mvc.perform(get("/api/student/requests/{id}/attachments/{file}", requestId, 99999999L)
                        .header("Authorization", token(a)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_FOUND"));
        mvc.perform(get("/api/student/requests/{id}/attachments/{file}", requestId, attachmentId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void physicalSubmissionCanOmitFilesOnlyForOptionalDocumentTypes() throws Exception {
        User student = student();
        DocumentType optional = documentType(false, false, true);
        mvc.perform(multipart("/api/student/requests")
                        .file(jsonPart(payload(optional.getId(), "Needed for employment confirmation.",
                                LocalDate.now().plusDays(2), "PHYSICAL")))
                        .header("Authorization", token(student)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(0))
                .andExpect(jsonPath("$.collectionLocation").value("Collection Desk"));
        DocumentType required = documentType(true, true, true);
        mvc.perform(multipart("/api/student/requests")
                        .file(jsonPart(payload(required.getId(), "Needed for university forms.",
                                LocalDate.now().plusDays(2), "PHYSICAL")))
                        .header("Authorization", token(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code")
                        .value("SUPPORTING_DOCUMENT_REQUIRED"));
    }

    @Test
    void validatesMultipartFieldsBadFilesAndUnusableCatalogueChoices() throws Exception {
        User student = student();
        DocumentType type = documentType(false, false, true);
        String valid = payload(type.getId(), "Reasonable purpose for a letter.", LocalDate.now(), "PHYSICAL");
        mvc.perform(multipart("/api/student/requests")
                        .file(jsonPart(payload(type.getId(), "x", LocalDate.now(), "PHYSICAL")))
                        .header("Authorization", token(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.purpose").exists());
        mvc.perform(multipart("/api/student/requests")
                        .file(jsonPart(payload(type.getId(), "Reasonable purpose for a letter.",
                                LocalDate.now().minusDays(1), "PHYSICAL")))
                        .header("Authorization", token(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.requiredDate").exists());
        mvc.perform(multipart("/api/student/requests")
                        .file(jsonPart(payload(type.getId(), "Reasonable purpose for a letter.",
                                LocalDate.now(), "ELECTRONIC")))
                        .header("Authorization", token(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DELIVERY_METHOD"));
        mvc.perform(multipart("/api/student/requests")
                        .file(jsonPart(payload(99999999L, "Reasonable purpose for a letter.",
                                LocalDate.now(), "PHYSICAL")))
                        .header("Authorization", token(student)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_FOUND"));
        mvc.perform(multipart("/api/student/requests")
                        .file(jsonPart(valid))
                        .file(new MockMultipartFile("files", "malicious.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", token(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        var fourth = multipart("/api/student/requests").file(jsonPart(valid));
        for (int n=0; n<4; n++) fourth.file(pdfPart(n + ".pdf"));
        mvc.perform(fourth.header("Authorization", token(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_COUNT_EXCEEDED"));
        mvc.perform(multipart("/api/student/requests")
                        .file(new MockMultipartFile("request", "", "application/json", "{invalid".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", token(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(multipart("/api/student/requests")
                        .file(jsonPart(payload(type.getId(), "Good purpose for file.", LocalDate.now(), "TELEPORT")))
                        .header("Authorization", token(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void rolesAndUnimplementedMutationsAreDenied() throws Exception {
        User student = student();
        User staff = user(UserRole.STAFF);
        User admin = user(UserRole.ADMIN);
        DocumentType type = documentType(false, true, true);
        String command = payload(type.getId(), "Required for a library card.", LocalDate.now(), "ELECTRONIC");
        mvc.perform(multipart("/api/student/requests").file(jsonPart(command)))
                .andExpect(status().isUnauthorized());
        for (User caller : new User[]{staff, admin}) {
            mvc.perform(multipart("/api/student/requests").file(jsonPart(command))
                            .header("Authorization", token(caller)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/student/requests").header("Authorization", token(caller)))
                    .andExpect(status().isForbidden());
        }
        for (String query : new String[]{"page=-1", "size=101", "status=INVALID", "deliveryMethod=FUTURE",
                "sort=student.passwordHash", "direction=sideways"}) {
            mvc.perform(get("/api/student/requests?" + query).header("Authorization", token(student)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
        mvc.perform(put("/api/student/requests/1").header("Authorization", token(student)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/student/requests/1").header("Authorization", token(student)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/student/requests/1").header("Authorization", token(student)))
                .andExpect(status().isMethodNotAllowed());
    }

    private User user(UserRole role) {
        return users.saveAndFlush(new User("req-http-" + UUID.randomUUID() + "@example.test", "hash", "HTTP Test Account", role));
    }
    private User student() {
        User user = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(user, "HTTP-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Faculty", "Department", "Computer Science", 3));
        return user;
    }
    private DocumentType documentType(boolean supporting, boolean electronic, boolean physical) {
        User admin = user(UserRole.ADMIN);
        String code = "HTTP" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return types.saveAndFlush(new DocumentType(code, "Document " + code,
                "A document for university administration.", "Bring required supporting records.",
                supporting, electronic, physical, physical ? "Collection Desk" : null,
                physical ? "Collect with student identification." : null, admin));
    }
    private static MockMultipartFile jsonPart(String json) {
        return new MockMultipartFile("request", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
    private static MockMultipartFile pdfPart(String filename) {
        return new MockMultipartFile("files", filename, "application/pdf", PDF);
    }
    private String token(User user) { return "Bearer " + jwt.issue(user).value(); }
    private static String payload(Long typeId, String purpose, LocalDate when, String delivery) {
        return "{\"documentTypeId\":" + typeId + ",\"purpose\":\"" + purpose +
                "\",\"requiredDate\":\"" + when + "\",\"deliveryMethod\":\"" + delivery + "\"}";
    }
}
