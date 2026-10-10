package com.unidoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(
        boolean seedEnabled,
        boolean resetEnabled,
        String resetConfirmation
) {
}
