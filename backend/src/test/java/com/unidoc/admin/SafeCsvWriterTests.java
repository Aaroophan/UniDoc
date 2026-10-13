package com.unidoc.admin;

import com.unidoc.request.DeliveryMethod;
import com.unidoc.request.RequestStatus;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SafeCsvWriterTests {
    @Test
    void emptyResultContainsHeaderAndOneCrLf() {
        assertThat(new String(SafeCsvWriter.write(List.of()), StandardCharsets.UTF_8))
                .isEqualTo(SafeCsvWriter.HEADER + "\r\n");
        assertThat(SafeCsvWriter.HEADER.split(",", -1)).hasSize(12);
    }

    @Test
    void unicodeQuotesCommasNewlinesAndSpreadsheetsAreEscapedInDocumentedOrder() {
        var row = new RequestReportRow("UDR-000123", "=HYPERLINK(1)", "José, \"Lee\"",
                "@example.test", "+Document", "=1+1\r\nSecond \"line\"", DeliveryMethod.PHYSICAL,
                LocalDate.parse("2026-10-12"), RequestStatus.READY,
                Instant.parse("2026-10-11T15:06:03Z"), Instant.parse("2026-10-12T08:00:00Z"), null);
        String csv = new String(SafeCsvWriter.write(List.of(row)), StandardCharsets.UTF_8);
        assertThat(csv).startsWith(SafeCsvWriter.HEADER + "\r\nUDR-000123,'=HYPERLINK(1),\"José, \"\"Lee\"\"\",'@example.test,'+Document,");
        assertThat(csv).contains("\"'=1+1\r\nSecond \"\"line\"\"\"");
        assertThat(csv).contains(",PHYSICAL,2026-10-12,READY,2026-10-11T15:06:03Z,2026-10-12T08:00:00Z,\r\n");
        assertThat(csv).doesNotContain("null");
    }

    @Test
    void leadingWhitespaceCannotBypassFormulaEscaping() {
        for (String suspicious : List.of("=1+1", "+SUM(1)", "-2", "@evil", "  =cmd", "\t+cmd")) {
            assertThat(SafeCsvWriter.cell(suspicious)).startsWith("'");
        }
        assertThat(SafeCsvWriter.cell("-2")).isEqualTo("'-2");
        assertThat(SafeCsvWriter.cell("normal")).isEqualTo("normal");
        assertThat(SafeCsvWriter.cell("")).isEmpty();
        assertThat(SafeCsvWriter.cell(null)).isEmpty();
    }
}
