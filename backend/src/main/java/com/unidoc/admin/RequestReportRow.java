package com.unidoc.admin;

import com.unidoc.request.DeliveryMethod;
import com.unidoc.request.RequestStatus;
import java.time.Instant;
import java.time.LocalDate;

/** Stable report row, shared unchanged by paginated JSON and CSV. */
public record RequestReportRow(
        String requestNumber, String registrationNumber, String studentName, String studentEmail,
        String documentTypeName, String purpose, DeliveryMethod deliveryMethod, LocalDate requiredDate,
        RequestStatus status, Instant createdAt, Instant updatedAt, Instant completedAt) {
}
