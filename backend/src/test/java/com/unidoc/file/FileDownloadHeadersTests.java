package com.unidoc.file;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileDownloadHeadersTests {

    @Test
    void addsRequiredNonCacheableAttachmentHeaders() {
        HttpHeaders headers = FileDownloadHeaders.forDownload("C:\\tmp\\assignment.pdf", "application/pdf", 13);
        assertThat(headers.getContentType().toString()).isEqualTo("application/pdf");
        assertThat(headers.getContentLength()).isEqualTo(13);
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getCacheControl()).isEqualTo("private, no-store");
        assertThat(headers.getPragma()).isEqualTo("no-cache");
        assertThat(headers.getContentDisposition().getFilename()).isEqualTo("assignment.pdf");
        assertThat(headers.getContentDisposition().getType()).isEqualTo("attachment");
    }

    @Test
    void encodesUnicodeAndQuotesWithoutHeaderInjection() {
        HttpHeaders headers = FileDownloadHeaders.forDownload(
                "../../தமிழ்\"\r\nreport.pdf", "application/pdf", 9);
        String value = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(value).doesNotContain("\r", "\n", "../../", "%0D", "%0A");
        assertThat(headers.getContentDisposition().getFilename()).contains("தமிழ்", "report.pdf");
    }

    @Test
    void rejectsUnapprovedResponseMediaTypesAndBadLengths() {
        assertThatThrownBy(() -> FileDownloadHeaders.forDownload("x.exe", "text/html", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileDownloadHeaders.forDownload("x.pdf", "application/pdf", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
