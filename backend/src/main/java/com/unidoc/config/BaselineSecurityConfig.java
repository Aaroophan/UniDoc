package com.unidoc.config;

/**
 * Compatibility tombstone for the Issue 3 baseline security configuration.
 *
 * Issue 4 owns the application's security filter chain through SecurityConfig.
 * Keeping this source file intentionally inert makes an Issue 4 archive safe to
 * extract over an older Issue 3 working tree: the legacy @Configuration and
 * securityFilterChain bean are overwritten instead of being left behind.
 */
@Deprecated(forRemoval = true)
public final class BaselineSecurityConfig {
    private BaselineSecurityConfig() {
    }
}
