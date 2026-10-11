package com.unidoc.config;

import com.unidoc.auth.ActiveUserFilter;
import com.unidoc.common.web.ApiErrorWriter;
import com.unidoc.user.UserRepository;
import com.unidoc.common.web.JsonAccessDeniedHandler;
import com.unidoc.common.web.JsonAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    ActiveUserFilter activeUserFilter(UserRepository userRepository, ApiErrorWriter apiErrorWriter) {
        return new ActiveUserFilter(userRepository, apiErrorWriter);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ActiveUserFilter activeUserFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/staff/**").hasRole("STAFF")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/notifications/**", "/api/document-types", "/api/document-types/**", "/api/auth/me").authenticated()
                        .anyRequest().denyAll())
                .addFilterAfter(activeUserFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    FilterRegistrationBean<ActiveUserFilter> activeUserFilterRegistration(ActiveUserFilter activeUserFilter) {
        FilterRegistrationBean<ActiveUserFilter> registration = new FilterRegistrationBean<>(activeUserFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(normalizeOrigins(properties.allowedOrigins()));
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "X-Request-ID"
        ));
        configuration.setExposedHeaders(List.of(
                HttpHeaders.CONTENT_DISPOSITION,
                "X-Request-ID"
        ));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    static List<String> normalizeOrigins(List<String> configuredOrigins) {
        List<String> normalized = new ArrayList<>();
        for (String configured : configuredOrigins) {
            if (configured == null || configured.isBlank()) {
                continue;
            }
            String candidate = configured.trim();
            if ("*".equals(candidate)) {
                throw new IllegalStateException("Wildcard CORS origins are not allowed.");
            }
            URI uri;
            try {
                uri = new URI(candidate);
            } catch (URISyntaxException exception) {
                throw new IllegalStateException("Configured CORS origin is invalid.", exception);
            }
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                throw new IllegalStateException("Configured CORS origin must be an HTTP(S) origin without a path.");
            }
            String authority = uri.getRawAuthority();
            normalized.add(uri.getScheme().toLowerCase() + "://" + authority);
        }
        if (normalized.isEmpty()) {
            throw new IllegalStateException("At least one CORS origin must be configured.");
        }
        return List.copyOf(normalized);
    }
}
