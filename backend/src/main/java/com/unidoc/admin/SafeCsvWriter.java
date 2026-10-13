package com.unidoc.admin;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** RFC-4180-style quoting and conservative spreadsheet-formula protection. */
public final class SafeCsvWriter {
    public static final String HEADER = "Request Number,Registration Number,Student Name,Student Email,Document Type,"
            + "Purpose,Delivery Method,Required Date,Status,Submitted At,Updated At,Completed At";
    private SafeCsvWriter() { }

    public static byte[] write(List<RequestReportRow> rows) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (RequestReportRow row : rows) {
            String[] cells = {
                    row.requestNumber(), row.registrationNumber(), row.studentName(), row.studentEmail(),
                    row.documentTypeName(), row.purpose(), str(row.deliveryMethod()), str(row.requiredDate()),
                    str(row.status()), str(row.createdAt()), str(row.updatedAt()), str(row.completedAt())
            };
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) csv.append(',');
                csv.append(cell(cells[i]));
            }
            csv.append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String str(Object value) { return value == null ? "" : value.toString(); }

    /** Prefix formula-looking cells before RFC-style quoting (including leading whitespace evasions). */
    static String cell(String value) {
        if (value == null || value.isEmpty()) return "";
        int first = 0;
        while (first < value.length() && Character.isWhitespace(value.charAt(first))) first++;
        if (first < value.length() && "=+-@".indexOf(value.charAt(first)) >= 0) value = "'" + value;
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0 ||
                value.indexOf('\n') >= 0) return '"' + value.replace("\"", "\"\"") + '"';
        return value;
    }
}
