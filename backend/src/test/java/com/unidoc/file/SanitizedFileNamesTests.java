package com.unidoc.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SanitizedFileNamesTests {

    @Test
    void stripsWindowsAndUnixPathSegmentsWithoutLosingUnicode() {
        assertThat(SanitizedFileNames.sanitize("../folder/தமிழ் report.pdf", "attachment.pdf"))
                .isEqualTo("தமிழ் report.pdf");
        assertThat(SanitizedFileNames.sanitize("C:\\sensitive\\nested\\Student Photo.jpeg", "attachment.jpg"))
                .isEqualTo("Student Photo.jpeg");
    }

    @Test
    void removesControlHeaderBreakersAndUnsafePunctuation() {
        assertThat(SanitizedFileNames.sanitize("dir/\"a\r\nb\t;:*.pdf\"", "attachment.pdf"))
                .isEqualTo("ab;.pdf");
        assertThat(SanitizedFileNames.sanitize("\u202Ereversed.pdf", "attachment.pdf"))
                .isEqualTo("reversed.pdf");
    }

    @Test
    void handlesEmptyNamesUnicodeLengthAndWindowsDeviceNames() {
        assertThat(SanitizedFileNames.sanitize(null, "document.pdf")).isEqualTo("document.pdf");
        assertThat(SanitizedFileNames.sanitize("\\folder\\", "attachment.png")).isEqualTo("attachment.png");
        assertThat(SanitizedFileNames.sanitize("     ", "attachment.png")).isEqualTo("attachment.png");
        assertThat(SanitizedFileNames.sanitize("CON.pdf", "attachment.pdf")).isEqualTo("file-CON.pdf");
        String longName = "文".repeat(240) + ".pdf";
        String sanitized = SanitizedFileNames.sanitize(longName, "attachment.pdf");
        assertThat(sanitized.codePointCount(0, sanitized.length())).isLessThanOrEqualTo(160);
        assertThat(sanitized).endsWith(".pdf");
    }
}
