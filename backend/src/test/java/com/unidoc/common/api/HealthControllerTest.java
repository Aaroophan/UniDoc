package com.unidoc.common.api;

import com.unidoc.common.web.ApiErrorWriter;
import com.unidoc.common.web.JsonAccessDeniedHandler;
import com.unidoc.common.web.JsonAuthenticationEntryPoint;
import com.unidoc.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({
        HealthControllerTest.TestSecurityConfiguration.class,
        ApiErrorWriter.class,
        JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class,
        RequestIdFilter.class
})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsMinimalPublicHealthResponse() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME));
    }

    @Test
    void deniesUnconfiguredRoutesWithSharedAuthenticationError() throws Exception {
        mockMvc.perform(get("/api/not-configured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().doesNotExist("Set-Cookie"));
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
                            .requestMatchers("/api/health").permitAll()
                            .anyRequest().authenticated());
            return http.build();
        }
    }
}
