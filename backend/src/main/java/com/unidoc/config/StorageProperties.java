package com.unidoc.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/** Server-owned bounds; the multipart container remains a separate, coarse 10/20 MB guard. */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotNull Path root,
        @Min(1) @Max(10 * 1024 * 1024) long supportingMaxBytes,
        @Min(1) @Max(10 * 1024 * 1024) long issuedMaxBytes
) {
}
