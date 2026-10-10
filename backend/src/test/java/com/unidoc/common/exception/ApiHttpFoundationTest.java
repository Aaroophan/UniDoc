package com.unidoc.common.exception;

import com.unidoc.common.api.PageResponse;
import com.unidoc.common.web.ApiErrorWriter;
import com.unidoc.common.web.JsonAccessDeniedHandler;
import com.unidoc.common.web.JsonAuthenticationEntryPoint;
import com.unidoc.common.web.RequestIdFilter;
import com.unidoc.file.FileStorageException;
import com.unidoc.request.DocumentRequest;
import com.unidoc.request.RequestStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApiHttpFoundationTest.TestController.class)
@Import({
        ApiHttpFoundationTest.TestController.class,
        GlobalExceptionHandler.class,
        RequestIdFilter.class,
        ApiErrorWriter.class,
        JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class,
        ApiHttpFoundationTest.TestSecurityConfiguration.class
})
class ApiHttpFoundationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void springFallbackErrorExposureRemainsDisabled() {
        assertThat(environment.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(environment.getProperty("server.error.include-binding-errors")).isEqualTo("never");
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(environment.getProperty("server.error.include-exception")).isEqualTo("false");
    }

    @Test
    void pageResponseSerializesWithTheDocumentedShape() throws Exception {
        mockMvc.perform(get("/test/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]").value("alpha"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void validationUsesOneErrorShapeAndDeterministicFieldOrdering() throws Exception {
        String body = mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zeta\":\"\",\"alpha\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.fieldErrors.alpha").value("Alpha must be at least 1."))
                .andExpect(jsonPath("$.fieldErrors.zeta").value("Zeta is required."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body.indexOf("\"alpha\"")).isLessThan(body.indexOf("\"zeta\""));
    }

    @Test
    void malformedJsonAndInvalidEnumUseStableTransportCodes() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(get("/test/enum").param("status", "WAITING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void knownAndUnknownIntegrityFailuresNeverExposeDatabaseText() throws Exception {
        mockMvc.perform(get("/test/duplicate-known"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("An account already uses this email address."));

        String marker = "MARKER_SQL_SECRET_92831";
        mockMvc.perform(get("/test/duplicate-unknown").param("marker", marker))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(marker))));
    }

    @Test
    void optimisticLockAndStorageFailuresMapToDocumentedCodes() throws Exception {
        mockMvc.perform(get("/test/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_VERSION_CONFLICT"));

        mockMvc.perform(get("/test/storage"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("FILE_STORAGE_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:\\\\private\\\\issued.pdf")
                )));
    }

    @Test
    void generic500IsRedactedAndCarriesTheSameRequestIdAsTheHeader() throws Exception {
        String requestId = "client-request-1234";
        String marker = "TOP_SECRET_MARKER_741";

        mockMvc.perform(get("/test/unexpected")
                        .header(RequestIdFilter.HEADER_NAME, requestId)
                        .param("marker", marker))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(marker))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:\\\\private\\\\secret.txt")
                )));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void requestIdIsGeneratedWhenMissingAndInvalidClientValuesAreReplaced() throws Exception {
        String generated = mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andReturn()
                .getResponse()
                .getHeader(RequestIdFilter.HEADER_NAME);

        String replaced = mockMvc.perform(get("/test/ok")
                        .header(RequestIdFilter.HEADER_NAME, "bad id with spaces"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(RequestIdFilter.HEADER_NAME);

        assertThat(generated).isNotBlank();
        assertThat(replaced).isNotBlank().isNotEqualTo("bad id with spaces");
    }

    @Test
    void anonymousSecurityFailureUsesTheSharedJsonErrorContractWithoutCreatingSession() throws Exception {
        mockMvc.perform(get("/secure/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void authenticatedWrongRoleUsesTheSharedJsonAccessDeniedContract() throws Exception {
        mockMvc.perform(get("/secure/admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action."));
    }

    @RestController
    public static class TestController {

        @GetMapping("/test/page")
        PageResponse<String> page() {
            return PageResponse.from(new PageImpl<>(
                    List.of("alpha"),
                    PageRequest.of(0, 20),
                    1
            ));
        }

        @GetMapping("/test/ok")
        Map<String, String> ok() {
            return Map.of("status", "ok");
        }

        @PostMapping("/test/validate")
        Map<String, String> validate(@Valid @RequestBody ValidationBody body) {
            return Map.of("status", "ok");
        }

        @GetMapping("/test/enum")
        Map<String, String> enumValue(@RequestParam RequestStatus status) {
            return Map.of("status", status.name());
        }

        @GetMapping("/test/duplicate-known")
        void duplicateKnown() {
            throw new DataIntegrityViolationException("duplicate constraint uq_users_email");
        }

        @GetMapping("/test/duplicate-unknown")
        void duplicateUnknown(@RequestParam String marker) {
            throw new DataIntegrityViolationException("unknown database conflict " + marker);
        }

        @GetMapping("/test/optimistic")
        void optimistic() {
            throw new ObjectOptimisticLockingFailureException(DocumentRequest.class, 1L);
        }

        @GetMapping("/test/storage")
        void storage() {
            throw new FileStorageException(new IllegalStateException("C:\\private\\issued.pdf"));
        }

        @GetMapping("/test/unexpected")
        void unexpected(@RequestParam String marker) {
            throw new IllegalStateException(marker + " C:\\private\\secret.txt");
        }

        @GetMapping("/secure/admin")
        Map<String, String> adminOnly() {
            return Map.of("status", "admin");
        }
    }

    record ValidationBody(
            @NotBlank(message = "Zeta is required.") String zeta,
            @Min(value = 1, message = "Alpha must be at least 1.") int alpha
    ) {
    }

    @TestConfiguration
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain testSecurityFilterChain(
                HttpSecurity http,
                JsonAuthenticationEntryPoint authenticationEntryPoint,
                JsonAccessDeniedHandler accessDeniedHandler
        ) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .logout(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/test/**").permitAll()
                            .requestMatchers("/secure/admin").hasRole("ADMIN")
                            .anyRequest().authenticated());
            return http.build();
        }
    }
}
