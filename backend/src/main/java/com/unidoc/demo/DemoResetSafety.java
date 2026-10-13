package com.unidoc.demo;

import com.unidoc.config.DemoProperties;

import java.util.regex.Pattern;

/** Defense-in-depth: an intentionally narrow local MySQL schema and two reset guards. */
public final class DemoResetSafety {
    private static final Pattern LOCAL_UNIDOC_URL = Pattern.compile(
            "^jdbc:mysql://(localhost|127\\.0\\.0\\.1|\\[::1\\])(?::[0-9]{1,5})?/unidoc(?:\\?.*)?$"
    );

    private DemoResetSafety() { }

    public static void requireGuards(DemoProperties properties, String serverPort) {
        if (!properties.resetEnabled()
                || !"RESET-UNIDOC-DEMO".equals(properties.resetConfirmation())
                || !"0".equals(serverPort)) {
            throw new IllegalStateException("Demo reset refused: explicit flags, confirmation token and SERVER_PORT=0 are required.");
        }
    }

    public static void requireLocalUnidoc(String url, String currentCatalog, String currentDatabase) {
        if (url == null || !LOCAL_UNIDOC_URL.matcher(url).matches()
                || !"unidoc".equals(currentCatalog)
                || !"unidoc".equals(currentDatabase)) {
            throw new IllegalStateException("Demo reset refused: the connection must target local MySQL schema 'unidoc'.");
        }
    }
}
