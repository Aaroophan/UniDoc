package com.unidoc.demo;

import com.unidoc.config.DemoProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoResetSafetyTests {
    private static final String GOOD =
            "jdbc:mysql://localhost:3306/unidoc?useSSL=false&serverTimezone=UTC";

    @Test
    void requiresBothBackendFlagsAndNonWebPort() {
        assertThatThrownBy(() -> DemoResetSafety.requireGuards(
                new DemoProperties(false, false, "RESET-UNIDOC-DEMO"), "0"))
                .hasMessageContaining("refused");
        assertThatThrownBy(() -> DemoResetSafety.requireGuards(
                new DemoProperties(false, true, "RESET-UNIDOC-DEMO-wrong"), "0"))
                .hasMessageContaining("refused");
        assertThatThrownBy(() -> DemoResetSafety.requireGuards(
                new DemoProperties(false, true, "RESET-UNIDOC-DEMO"), "8080"))
                .hasMessageContaining("refused");
        assertThatCode(() -> DemoResetSafety.requireGuards(
                new DemoProperties(false, true, "RESET-UNIDOC-DEMO"), "0"))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesWrongSchemaRemoteOrMismatchedCurrentDatabase() {
        assertThatCode(() -> DemoResetSafety.requireLocalUnidoc(GOOD, "unidoc", "unidoc"))
                .doesNotThrowAnyException();
        for (String wrong : new String[] {
                "jdbc:mysql://localhost:3306/other", "jdbc:mysql://db.example.org:3306/unidoc",
                "jdbc:h2:mem:unidoc", "jdbc:mysql://localhost:3306/unidoc_bak"
        }) {
            assertThatThrownBy(() -> DemoResetSafety.requireLocalUnidoc(wrong, "unidoc", "unidoc"))
                    .hasMessageContaining("refused");
        }
        assertThatThrownBy(() -> DemoResetSafety.requireLocalUnidoc(GOOD, "other", "unidoc"))
                .hasMessageContaining("refused");
        assertThatThrownBy(() -> DemoResetSafety.requireLocalUnidoc(GOOD, "unidoc", "other"))
                .hasMessageContaining("refused");
    }
}
