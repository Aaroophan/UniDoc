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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-physical-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=", "UNIDOC_EMAIL_ENABLED=false",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/issue16-physical-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class PhysicalCollectionHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService jwt;
    @Autowired DocumentRequestService students;
    @Autowired StaffRequestService staff;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired DocumentTypeRepository types;

    @Test
    void fullPhysicalApiSecurityValidationVersionAndReadOnlyStudentDetails() throws Exception {
        User staffUser = user(UserRole.STAFF);
        User owner = student();
        User stranger = student();
        User admin = user(UserRole.ADMIN);
        var processing = processing(owner, staffUser, DeliveryMethod.PHYSICAL);
        String readyPath = "/api/staff/requests/" + processing.id() + "/ready-for-collection";
        String completePath = "/api/staff/requests/" + processing.id() + "/complete-collection";
        for (User forbidden : List.of(owner, admin)) {
            mvc.perform(post(readyPath).header("Authorization", token(forbidden)).contentType(MediaType.APPLICATION_JSON)
                    .content(version(processing.version()))).andExpect(status().isForbidden());
            mvc.perform(post(completePath).header("Authorization", token(forbidden)).contentType(MediaType.APPLICATION_JSON)
                    .content(collection(LocalDate.now(), null, processing.version()))).andExpect(status().isForbidden());
        }
        mvc.perform(post(readyPath).contentType(MediaType.APPLICATION_JSON).content(version(processing.version())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(completePath).contentType(MediaType.APPLICATION_JSON)
                .content(collection(LocalDate.now(), null, processing.version())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(readyPath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(readyPath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content(version(-1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(completePath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content(collection(LocalDate.now(), "note", processing.version())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_TRANSITION"));
        mvc.perform(post(readyPath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content(version(processing.version())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.issuedDocument").doesNotExist())
                .andExpect(jsonPath("$.collectionLocation").value("Original Records Office"))
                .andExpect(jsonPath("$.availableActions[0]").value("COMPLETE_COLLECTION"));
        var ready = staff.getDetail(staffUser.getId(), processing.id());
        mvc.perform(post(readyPath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content(version(processing.version())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_VERSION_CONFLICT"));
        mvc.perform(post(completePath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content(collection(LocalDate.now().plusDays(1), "future", ready.version())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.collectionDate").exists());
        mvc.perform(post(completePath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + ready.version() + ",\"note\":\"missing date\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.collectionDate").exists());
        mvc.perform(post(completePath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content(collection(LocalDate.now(), "X".repeat(1001), ready.version())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.note").exists());
        mvc.perform(post(completePath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content(collection(LocalDate.now(), "ID was checked", ready.version())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.collectionDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.collectionNote").value("ID was checked"))
                .andExpect(jsonPath("$.history[5].newStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.availableActions.length()").value(0));
        var completed = staff.getDetail(staffUser.getId(), processing.id());
        mvc.perform(post(completePath).header("Authorization", token(staffUser)).contentType(MediaType.APPLICATION_JSON)
                .content(collection(LocalDate.now(), "repeat", completed.version())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_TRANSITION"));
        mvc.perform(get("/api/student/requests/{id}", processing.id())
                .header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.collectionDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.collectionLocation").value("Original Records Office"));
        mvc.perform(get("/api/student/requests/{id}", processing.id())
                .header("Authorization", token(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/student/requests/{id}/issued-document", processing.id())
                .header("Authorization", token(owner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_METHOD_MISMATCH"));
        mvc.perform(get("/api/staff/requests/{id}/issued-document", processing.id())
                .header("Authorization", token(staffUser)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_METHOD_MISMATCH"));
        mvc.perform(multipart("/api/staff/requests/{id}/issued-document", processing.id())
                .file(new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .param("expectedVersion", String.valueOf(completed.version()))
                .header("Authorization", token(staffUser)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_METHOD_MISMATCH"));
    }

    @Test
    void electronicRequestsCannotUseEitherPhysicalStaffEndpoint() throws Exception {
        User reviewer = user(UserRole.STAFF);
        User owner = student();
        var processing = processing(owner, reviewer, DeliveryMethod.ELECTRONIC);
        mvc.perform(post("/api/staff/requests/{id}/ready-for-collection", processing.id())
                .header("Authorization", token(reviewer)).contentType(MediaType.APPLICATION_JSON)
                .content(version(processing.version())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_METHOD_MISMATCH"));
        mvc.perform(post("/api/staff/requests/{id}/complete-collection", processing.id())
                .header("Authorization", token(reviewer)).contentType(MediaType.APPLICATION_JSON)
                .content(collection(LocalDate.now(), null, processing.version())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_METHOD_MISMATCH"));
    }

    private String version(long value) { return "{\"expectedVersion\":" + value + "}"; }
    private String collection(LocalDate date, String note, long version) {
        return "{\"collectionDate\":\"" + date + "\",\"expectedVersion\":" + version +
                (note == null ? "}" : ",\"note\":\"" + note + "\"}");
    }
    private String token(User user) { return "Bearer " + jwt.issue(user).value(); }
    private User user(UserRole role) {
        return users.saveAndFlush(new User("phy-http-" + UUID.randomUUID() + "@example.test", "hash", "Physical HTTP", role));
    }
    private User student() {
        User owner = user(UserRole.STUDENT);
        profiles.saveAndFlush(new StudentProfile(owner, "PHYH-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Science", "Computing", "Computer Science", 2));
        return owner;
    }
    private StaffRequestDetailResponse processing(User owner, User reviewer, DeliveryMethod method) {
        User admin = user(UserRole.ADMIN);
        String code = "PHYH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        DocumentType type = types.saveAndFlush(new DocumentType(code, "Physical HTTP " + code,
                "Physical HTTP test document.", "Bring university identification", false, true, true,
                "Original Records Office", "Bring university identification.", admin));
        var submitted = students.create(owner.getId(), new CreateDocumentRequest(type.getId(),
                "Needed for programme application", LocalDate.now().plusDays(5), method), List.of());
        var reviewed = staff.startReview(reviewer.getId(), submitted.id(), submitted.version());
        var approved = staff.approve(reviewer.getId(), submitted.id(), reviewed.version());
        return staff.startProcessing(reviewer.getId(), submitted.id(), approved.version());
    }
}
