package com.unidoc.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityConfigTests {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final JwtSecurityConfig configuration = new JwtSecurityConfig();

    @Test
    void acceptsBase64SecretOfAtLeastThirtyTwoBytesAndForcesHs256() {
        JwtProperties properties = new JwtProperties(SECRET, "unidoc", 3600);
        SecretKey key = configuration.jwtSecretKey(properties);
        JwtEncoder encoder = configuration.jwtEncoder(key);
        JwtDecoder decoder = configuration.jwtDecoder(key, properties);

        Instant now = Instant.now();
        String token = encode(encoder, "1", "unidoc", now, now.plusSeconds(3600), "STUDENT", 0);

        assertThat(decoder.decode(token).getHeaders().get("alg")).isEqualTo("HS256");
    }

    @Test
    void rejectsMalformedOrShortSecretAndInvalidExpiryConfiguration() {
        assertThatThrownBy(() -> configuration.jwtSecretKey(new JwtProperties("%%%", "unidoc", 3600)))
                .isInstanceOf(IllegalStateException.class);

        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> configuration.jwtSecretKey(new JwtProperties(shortSecret, "unidoc", 3600)))
                .isInstanceOf(IllegalStateException.class);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(new JwtProperties(SECRET, "unidoc", 299))).isNotEmpty();
            assertThat(validator.validate(new JwtProperties(SECRET, "unidoc", 86401))).isNotEmpty();
            assertThat(validator.validate(new JwtProperties(SECRET, "unidoc", 3600))).isEmpty();
        }
    }

    @Test
    void decoderRejectsInvalidSignatureExpiryIssuerSubjectAndRole() {
        JwtProperties properties = new JwtProperties(SECRET, "unidoc", 3600);
        SecretKey key = configuration.jwtSecretKey(properties);
        JwtDecoder decoder = configuration.jwtDecoder(key, properties);
        JwtEncoder encoder = configuration.jwtEncoder(key);
        Instant now = Instant.now();

        SecretKey otherKey = new SecretKeySpec(
                "fedcba9876543210fedcba9876543210".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        JwtEncoder otherEncoder = NimbusJwtEncoder.withSecretKey(otherKey)
                .algorithm(MacAlgorithm.HS256)
                .build();

        assertThatThrownBy(() -> decoder.decode(encode(
                otherEncoder, "1", "unidoc", now, now.plusSeconds(3600), "STUDENT", 0
        ))).isInstanceOf(JwtException.class);

        assertThatThrownBy(() -> decoder.decode(encode(
                encoder, "1", "unidoc", now.minusSeconds(7200), now.minusSeconds(3600), "STUDENT", 0
        ))).isInstanceOf(JwtException.class);

        assertThatThrownBy(() -> decoder.decode(encode(
                encoder, "1", "other-issuer", now, now.plusSeconds(3600), "STUDENT", 0
        ))).isInstanceOf(JwtException.class);

        assertThatThrownBy(() -> decoder.decode(encode(
                encoder, "not-a-number", "unidoc", now, now.plusSeconds(3600), "STUDENT", 0
        ))).isInstanceOf(JwtException.class);

        assertThatThrownBy(() -> decoder.decode(encode(
                encoder, "1", "unidoc", now, now.plusSeconds(3600), "SUPERUSER", 0
        ))).isInstanceOf(JwtException.class);
    }

    private static String encode(
            JwtEncoder encoder,
            String subject,
            String issuer,
            Instant issuedAt,
            Instant expiresAt,
            String role,
            int authVersion
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", "user@example.test")
                .claim("role", role)
                .claim("authVersion", authVersion)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
