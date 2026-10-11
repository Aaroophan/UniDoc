package com.unidoc.catalogue;

import tools.jackson.databind.ObjectMapper;
import com.unidoc.auth.JwtTokenService;
import com.unidoc.user.User;
import com.unidoc.user.UserRepository;
import com.unidoc.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "UNIDOC_DB_URL=jdbc:h2:mem:unidoc-catalogue-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "UNIDOC_DB_USERNAME=sa", "UNIDOC_DB_PASSWORD=",
        "UNIDOC_JWT_SECRET=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "UNIDOC_STORAGE_ROOT=target/catalogue-http-test-uploads",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class DocumentCatalogueHttpTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private DocumentTypeRepository types;
    @Autowired private DocumentCatalogueService service;
    @Autowired private JwtTokenService jwt;
    @Autowired private ObjectMapper mapper;

    @Test
    void anyAuthenticatedRoleCanReadActiveCatalogueButAnonymousCannot() throws Exception {
        User admin = account(UserRole.ADMIN);
        long activeId = service.create(admin.getId(), values(code(), "Zed Letter", true, true, true, "Reception")).id();
        long hiddenId = service.create(admin.getId(), values(code(), "A Hidden Letter", true, false, false, null)).id();
        long firstId = service.create(admin.getId(), values(code(), "Alpha Letter", false, true, true, "Collection Office")).id();
        for (UserRole role : UserRole.values()) {
            User caller = role == UserRole.ADMIN ? admin : account(role);
            String token = bearer(caller);
            mvc.perform(get("/api/document-types").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(firstId))
                    .andExpect(jsonPath("$[0].availableDeliveryMethods[0]").value("PHYSICAL"))
                    .andExpect(jsonPath("$[1].id").value(activeId))
                    .andExpect(jsonPath("$[2]").doesNotExist())
                    .andExpect(jsonPath("$[0].active").doesNotExist())
                    .andExpect(jsonPath("$[0].createdBy").doesNotExist());
            mvc.perform(get("/api/document-types/{id}", activeId).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableDeliveryMethods[0]").value("ELECTRONIC"))
                    .andExpect(jsonPath("$.availableDeliveryMethods[1]").value("PHYSICAL"))
                    .andExpect(jsonPath("$.collectionLocation").value("Reception"));
            mvc.perform(get("/api/document-types/{id}", hiddenId).header("Authorization", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_FOUND"));
            mvc.perform(get("/api/document-types/9999999").header("Authorization", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_FOUND"));
        }
        mvc.perform(get("/api/document-types")).andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/api/document-types/{id}", activeId)).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminCanUseAllFiveManagedEndpoints() throws Exception {
        User admin = account(UserRole.ADMIN);
        long id = service.create(admin.getId(), values(code(), "Protected Type", true, false, true, null)).id();
        String create = json(values(code(), "Another Protected Type", true, false, true, null));
        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.STAFF}) {
            String token = bearer(account(role));
            mvc.perform(get("/api/admin/document-types").header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/document-types/{id}", id).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/document-types").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/document-types/{id}", id).header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/document-types/{id}/status", id).header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/document-types")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/document-types/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/document-types").contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/document-types/{id}", id).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/document-types/{id}/status", id).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateReadUpdateDeactivateAndReactivateWithoutDeletion() throws Exception {
        User admin = account(UserRole.ADMIN);
        String token = bearer(admin);
        String entryCode = code();
        String body = json(values("  " + entryCode.toLowerCase() + " ", "Managed Document", true, true, true, "Initial Desk"));
        String response = mvc.perform(post("/api/admin/document-types").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(entryCode))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.authVersion").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(response).get("id").asLong();
        mvc.perform(get("/api/admin/document-types/{id}", id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(entryCode));
        mvc.perform(put("/api/admin/document-types/{id}", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(values(entryCode, "Updated Document", false, true, true, "New Collection Desk"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Document"))
                .andExpect(jsonPath("$.electronicAvailable").value(false))
                .andExpect(jsonPath("$.physicalAvailable").value(true))
                .andExpect(jsonPath("$.collectionLocation").value("New Collection Desk"));
        mvc.perform(patch("/api/admin/document-types/{id}/status", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/document-types/{id}", id).header("Authorization", token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_FOUND"));
        mvc.perform(get("/api/admin/document-types/{id}", id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/api/admin/document-types/{id}/status", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/document-types/{id}", id).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Document"));
        assertThat(types.existsById(id)).isTrue();
    }

    @Test
    void validationReportsCorrectFieldsForInvalidDeliveryAndContent() throws Exception {
        User admin = account(UserRole.ADMIN);
        String token = bearer(admin);
        mvc.perform(post("/api/admin/document-types").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(values(code(), "No Methods", false, false, true, null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.electronicAvailable").exists());
        mvc.perform(post("/api/admin/document-types").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(values(code(), "Missing Location", false, true, true, " "))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.collectionLocation").exists());
        mvc.perform(post("/api/admin/document-types").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad code!\",\"name\":\"a\",\"description\":\"short\",\"requirements\":\"x\",\"supportingDocumentsRequired\":true,\"electronicAvailable\":true,\"physicalAvailable\":false,\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.requirements").exists());
        mvc.perform(post("/api/admin/document-types").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CODE_ABC\",\"name\":\"Missing Flags\",\"description\":\"A long description.\",\"requirements\":\"Evidence required.\",\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.electronicAvailable").exists())
                .andExpect(jsonPath("$.fieldErrors.physicalAvailable").exists());
        mvc.perform(patch("/api/admin/document-types/1/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.active").exists());
    }

    @Test
    void caseInsensitiveCodeAndNameDuplicatesReturnSpecificConflictCodes() throws Exception {
        User admin = account(UserRole.ADMIN);
        String token = bearer(admin);
        String existingCode = code();
        service.create(admin.getId(), values(existingCode, "Transcript Letter", true, false, true, null));
        mvc.perform(post("/api/admin/document-types").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(values(existingCode.toLowerCase(), "Different Name", true, false, true, null))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_CODE_EXISTS"))
                .andExpect(jsonPath("$.requestId").exists());
        mvc.perform(post("/api/admin/document-types").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(values(code(), "tRaNsCrIpT lEtTeR", true, false, true, null))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NAME_EXISTS"));
    }

    @Test
    void adminListSupportsFiltersPaginationAndRejectsUnsupportedSortFields() throws Exception {
        User admin = account(UserRole.ADMIN);
        String marker = "H" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        service.create(admin.getId(), values(marker + "A", "Alpha " + marker, true, false, true, null));
        service.create(admin.getId(), values(marker + "B", "Beta " + marker, true, false, false, null));
        service.create(admin.getId(), values(marker + "C", "Gamma " + marker, true, false, true, null));
        String token = bearer(admin);
        mvc.perform(get("/api/admin/document-types").header("Authorization", token)
                        .param("q", marker).param("active", "false").param("sort", "name")
                        .param("direction", "asc").param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Beta " + marker))
                .andExpect(jsonPath("$.items[0].active").value(false))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1));
        for (String invalid : new String[]{"creator", "passwordHash", "updatedAt"}) {
            mvc.perform(get("/api/admin/document-types").header("Authorization", token).param("sort", invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
        mvc.perform(get("/api/admin/document-types").header("Authorization", token).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(get("/api/admin/document-types").header("Authorization", token).param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(get("/api/admin/document-types").header("Authorization", token).param("active", "maybe"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(get("/api/admin/document-types").header("Authorization", token).param("q", "q".repeat(101)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void neverOffersDeleteAndRejectsUnknownPositiveIds() throws Exception {
        User admin = account(UserRole.ADMIN);
        String token = bearer(admin);
        mvc.perform(get("/api/admin/document-types/9999999").header("Authorization", token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_FOUND"));
        mvc.perform(put("/api/admin/document-types/9999999").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(values(code(), "Does Not Exist", true, false, true, null))))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/document-types/9999999/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/document-types").header("Authorization", token))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private User account(UserRole role) {
        return users.saveAndFlush(new User("issue8-http-" + UUID.randomUUID() + "@example.test", "hash", "Catalogue HTTP Tester", role));
    }

    private String bearer(User user) { return "Bearer " + jwt.issue(user).value(); }
    private String json(UpsertDocumentTypeRequest body) throws Exception { return mapper.writeValueAsString(body); }
    private static String code() { return "T_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(); }

    private static UpsertDocumentTypeRequest values(String code, String name,
                                                     boolean electronic, boolean physical, boolean active, String location) {
        return new UpsertDocumentTypeRequest(code, name, "Official document details for students.",
                "Provide university identification.", true, electronic, physical, location, "Bring photo identification.", active);
    }
}
