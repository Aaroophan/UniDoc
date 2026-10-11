package com.unidoc.file;

import java.util.Locale;

/** Original names are untrusted display strings, never filesystem components. */
public final class SanitizedFileNames {

    private static final int MAX_DISPLAY_CODE_POINTS = 160;

    private SanitizedFileNames() {
    }

    public static String sanitize(String suppliedName, String fallback) {
        String name = suppliedName == null ? "" : suppliedName;
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(lastSeparator + 1);

        StringBuilder cleaned = new StringBuilder();
        name.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029
                    || (cp >= 0x202a && cp <= 0x202e) || (cp >= 0x2066 && cp <= 0x2069)
                    || "\"<>:|?*".indexOf(cp) >= 0 || !Character.isDefined(cp)
                    || (cp >= 0xd800 && cp <= 0xdfff)) {
                return;
            }
            if (Character.isWhitespace(cp)) {
                cleaned.append(' ');
            } else {
                cleaned.appendCodePoint(cp);
            }
        });
        String normalized = cleaned.toString().strip()
                .replaceAll(" {2,}", " ")
                .replaceAll("[. ]+$", "");
        if (normalized.codePointCount(0, normalized.length()) > MAX_DISPLAY_CODE_POINTS) {
            // Preserve a short extension while trimming a long basename; validation
            // must not reject an otherwise valid PDF solely because its name was long.
            int dot = normalized.lastIndexOf('.');
            String suffix = dot > 0 && normalized.length() - dot <= 12
                    ? normalized.substring(dot) : "";
            int available = MAX_DISPLAY_CODE_POINTS - suffix.codePointCount(0, suffix.length());
            String prefix = normalized.substring(0, normalized.offsetByCodePoints(0, available))
                    .replaceAll("[. ]+$", "");
            normalized = prefix + suffix;
        }
        if (normalized.isEmpty() || normalized.equals(".") || normalized.equals("..")) {
            return fallback;
        }
        // These are Windows device names and cannot be saved on the receiving desktop.
        String base = normalized.contains(".") ? normalized.substring(0, normalized.indexOf('.')) : normalized;
        if (base.toUpperCase(Locale.ROOT).matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            normalized = "file-" + normalized;
        }
        return normalized;
    }
}
